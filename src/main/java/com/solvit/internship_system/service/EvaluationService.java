package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.evaluation.AcknowledgeEvaluationRequestDto;
import com.solvit.internship_system.dto.evaluation.CreateEvaluationRequestDto;
import com.solvit.internship_system.dto.evaluation.EvaluationDto;
import com.solvit.internship_system.dto.evaluation.UpdateEvaluationDraftDto;
import com.solvit.internship_system.entity.Evaluation;
import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.mapper.EvaluationMapper;
import com.solvit.internship_system.repository.EvaluationRepository;
import com.solvit.internship_system.repository.ProjectGroupRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {
    private static final int MIN_ATTENDANCE_SCORE_FOR_EVALUATION = 70;

    private final EvaluationRepository evaluationRepository;
    private final UserRepository userRepository;
    private final ProjectGroupRepository projectGroupRepository;

    @Transactional
    public EvaluationDto create(CreateEvaluationRequestDto dto, Long evaluatorId) {
        User evaluator = userRepository.findById(evaluatorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", evaluatorId));
        if (evaluator.getRole() != Role.ADMIN && evaluator.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Only ADMIN or SUPERVISOR can create evaluations");
        }

        User intern = userRepository.findById(dto.getInternId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getInternId()));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("internId must reference an INTERN");
        }

        ProjectGroup group = null;
        if (dto.getGroupId() != null) {
            group = projectGroupRepository.findById(dto.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", dto.getGroupId()));
            if (!group.isActive()) {
                throw new BadRequestException("Project group is not active");
            }
        }

        assertNoDuplicateActive(intern.getId(), dto.getType(), group != null ? group.getId() : null);

        Evaluation e = Evaluation.builder()
                .intern(intern)
                .evaluator(evaluator)
                .group(group)
                .type(dto.getType())
                .status(Evaluation.EvaluationStatus.DRAFT)
                .technicalScore(dto.getTechnicalScore())
                .communicationScore(dto.getCommunicationScore())
                .attendanceScore(dto.getAttendanceScore())
                .initiativeScore(dto.getInitiativeScore())
                .strengthsNote(dto.getStrengthsNote())
                .improvementNote(dto.getImprovementNote())
                .supervisorComment(dto.getSupervisorComment())
                .evaluationDate(dto.getEvaluationDate())
                .internAcknowledged(false)
                .acknowledgedAt(null)
                .internResponse(null)
                .active(true)
                .build();

        if (e.getAttendanceScore() != null && e.getAttendanceScore() < MIN_ATTENDANCE_SCORE_FOR_EVALUATION) {
            throw new BadRequestException("Minimum attendance score for an evaluation is " + MIN_ATTENDANCE_SCORE_FOR_EVALUATION + "%");
        }

        return EvaluationMapper.toDto(evaluationRepository.save(e));
    }

    @Transactional
    public EvaluationDto updateDraft(Long id, UpdateEvaluationDraftDto dto, Long userId) {
        Evaluation e = getDetails(id);
        if (e.getStatus() != Evaluation.EvaluationStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT evaluations can be updated");
        }
        requireEvaluatorOrAdmin(e, userId);

        if (dto.getTechnicalScore() != null) {
            e.setTechnicalScore(dto.getTechnicalScore());
        }
        if (dto.getCommunicationScore() != null) {
            e.setCommunicationScore(dto.getCommunicationScore());
        }
        if (dto.getAttendanceScore() != null) {
            e.setAttendanceScore(dto.getAttendanceScore());
        }
        if (dto.getInitiativeScore() != null) {
            e.setInitiativeScore(dto.getInitiativeScore());
        }
        if (dto.getStrengthsNote() != null) {
            e.setStrengthsNote(dto.getStrengthsNote());
        }
        if (dto.getImprovementNote() != null) {
            e.setImprovementNote(dto.getImprovementNote());
        }
        if (dto.getSupervisorComment() != null) {
            e.setSupervisorComment(dto.getSupervisorComment());
        }
        if (dto.getEvaluationDate() != null) {
            e.setEvaluationDate(dto.getEvaluationDate());
        }

        return EvaluationMapper.toDto(evaluationRepository.save(e));
    }

    @Transactional
    public EvaluationDto submit(Long id, Long evaluatorId) {
        Evaluation e = getDetails(id);
        if (e.getStatus() != Evaluation.EvaluationStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT evaluations can be submitted");
        }
        User actor = userRepository.findById(evaluatorId).orElseThrow(() -> new ResourceNotFoundException("User", evaluatorId));
        if (actor.getRole() != Role.ADMIN && !Objects.equals(e.getEvaluator().getId(), evaluatorId)) {
            throw new AccessDeniedException("Only the evaluator or an admin can submit this evaluation");
        }
        if (e.getTechnicalScore() == null || e.getCommunicationScore() == null
                || e.getAttendanceScore() == null || e.getInitiativeScore() == null) {
            throw new BadRequestException("All four dimension scores are required before submit");
        }
        if (e.getAttendanceScore() < MIN_ATTENDANCE_SCORE_FOR_EVALUATION) {
            throw new BadRequestException("Minimum attendance score for submission is " + MIN_ATTENDANCE_SCORE_FOR_EVALUATION + "%");
        }
        e.setStatus(Evaluation.EvaluationStatus.SUBMITTED);
        return EvaluationMapper.toDto(evaluationRepository.save(e));
    }

    @Transactional
    public EvaluationDto acknowledge(Long id, Long internId, AcknowledgeEvaluationRequestDto dto) {
        Evaluation e = getDetails(id);
        if (e.getStatus() != Evaluation.EvaluationStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED evaluations can be acknowledged");
        }
        if (!Objects.equals(e.getIntern().getId(), internId)) {
            throw new AccessDeniedException("You can only acknowledge your own evaluations");
        }
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new AccessDeniedException("Only interns can acknowledge");
        }
        String response = dto.getInternResponse().trim();
        if (response.length() > 2000) {
            throw new BadRequestException("internResponse must be at most 2000 characters");
        }
        e.setInternResponse(response);
        e.setInternAcknowledged(true);
        e.setAcknowledgedAt(Instant.now());
        e.setStatus(Evaluation.EvaluationStatus.ACKNOWLEDGED);
        return EvaluationMapper.toDto(evaluationRepository.save(e));
    }

    @Transactional(readOnly = true)
    public EvaluationDto getById(Long id, Long requesterId) {
        Evaluation e = getDetails(id);
        authorizeRead(e, requesterId);
        return EvaluationMapper.toDto(e);
    }

    @Transactional(readOnly = true)
    public Page<EvaluationDto> list(
            Long internId,
            Long groupId,
            Evaluation.EvaluationType type,
            Evaluation.EvaluationStatus status,
            Pageable pageable,
            Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        Long supervisorScope = null;
        if (requester.getRole() == Role.SUPERVISOR) {
            supervisorScope = requesterId;
        } else if (requester.getRole() != Role.ADMIN && requester.getRole() != Role.HR) {
            throw new AccessDeniedException("Not allowed to list evaluations");
        }
        Pageable p = pageable;
        if (!p.getSort().isSorted()) {
            p = PageRequest.of(p.getPageNumber(), p.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return evaluationRepository
                .searchFiltered(internId, groupId, type, status, supervisorScope, p)
                .map(EvaluationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<EvaluationDto> listForIntern(Long internUserId, int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return evaluationRepository.findByIntern_IdAndStatusInAndActiveTrue(
                internUserId,
                List.of(Evaluation.EvaluationStatus.SUBMITTED, Evaluation.EvaluationStatus.ACKNOWLEDGED),
                p
        ).map(EvaluationMapper::toDto);
    }

    @Transactional
    public void softDelete(Long id, Long adminId) {
        User u = userRepository.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User", adminId));
        if (u.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN can delete evaluations");
        }
        Evaluation e = evaluationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evaluation", id));
        e.setActive(false);
        evaluationRepository.save(e);
        log.info("Soft-deleted evaluation id={}", id);
    }

    private void assertNoDuplicateActive(Long internId, Evaluation.EvaluationType type, Long groupId) {
        boolean dup;
        if (groupId != null) {
            dup = evaluationRepository.existsByIntern_IdAndTypeAndGroup_IdAndActiveTrue(internId, type, groupId);
        } else {
            dup = evaluationRepository.existsByIntern_IdAndTypeAndGroupIsNullAndActiveTrue(internId, type);
        }
        if (dup) {
            throw new BadRequestException("An active evaluation of this type already exists for this intern in this scope");
        }
    }

    private Evaluation getDetails(Long id) {
        return evaluationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation", id));
    }

    private void authorizeRead(Evaluation e, Long requesterId) {
        User u = userRepository.findById(requesterId).orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        if (u.getRole() == Role.ADMIN || u.getRole() == Role.HR) {
            return;
        }
        if (u.getRole() == Role.INTERN && Objects.equals(e.getIntern().getId(), requesterId)) {
            return;
        }
        if (u.getRole() == Role.SUPERVISOR) {
            if (Objects.equals(e.getEvaluator().getId(), requesterId)) {
                return;
            }
            if (e.getGroup() != null
                    && e.getGroup().getSupervisor() != null
                    && Objects.equals(e.getGroup().getSupervisor().getId(), requesterId)) {
                return;
            }
        }
        throw new AccessDeniedException("Not allowed to view this evaluation");
    }

    private void requireEvaluatorOrAdmin(Evaluation e, Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (u.getRole() == Role.ADMIN) {
            return;
        }
        if (Objects.equals(e.getEvaluator().getId(), userId)) {
            return;
        }
        throw new AccessDeniedException("Only the evaluator or an admin can update this draft");
    }
}
