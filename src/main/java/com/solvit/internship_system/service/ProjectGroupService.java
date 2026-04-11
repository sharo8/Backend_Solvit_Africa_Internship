package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.groups.CreateProjectGroupRequestDto;
import com.solvit.internship_system.dto.groups.ProjectGroupDto;
import com.solvit.internship_system.dto.groups.UpdateProjectGroupRequestDto;
import com.solvit.internship_system.dto.tasks.UserSummaryDto;
import com.solvit.internship_system.entity.GroupStatus;
import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.ProjectGroupRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectGroupService {

    private final ProjectGroupRepository projectGroupRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public ProjectGroupDto create(CreateProjectGroupRequestDto dto, Long createdById) {
        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new ResourceNotFoundException("User", createdById));
        if (createdBy.getRole() != Role.ADMIN && createdBy.getRole() != Role.HR) {
            throw new AccessDeniedException("Only ADMIN or HR can create project groups");
        }

        User supervisor = userRepository.findById(dto.getSupervisorId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getSupervisorId()));
        if (supervisor.getRole() != Role.SUPERVISOR) {
            throw new BadRequestException("supervisorId must reference a user with role SUPERVISOR");
        }

        ProjectGroup g = ProjectGroup.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .supervisor(supervisor)
                .createdBy(createdBy)
                .interns(new ArrayList<>())
                .status(GroupStatus.ACTIVE)
                .active(true)
                .build();

        return toDto(projectGroupRepository.save(g));
    }

    @Transactional(readOnly = true)
    public Page<ProjectGroupDto> list(Pageable pageable, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        Page<ProjectGroup> page;
        if (requester.getRole() == Role.SUPERVISOR) {
            page = projectGroupRepository.findByActiveTrueAndSupervisor_Id(requesterId, pageable);
        } else if (requester.getRole() == Role.ADMIN || requester.getRole() == Role.HR) {
            page = projectGroupRepository.findByActiveTrue(pageable);
        } else {
            throw new AccessDeniedException("Not allowed to list project groups");
        }
        return page.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProjectGroupDto getById(Long id, Long requesterId) {
        ProjectGroup g = projectGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", id));
        if (!g.isActive()) {
            throw new ResourceNotFoundException("ProjectGroup", id);
        }
        authorizeRead(g, requesterId);
        return toDto(g);
    }

    @Transactional
    public ProjectGroupDto update(Long id, UpdateProjectGroupRequestDto dto, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        if (requester.getRole() != Role.ADMIN && requester.getRole() != Role.HR) {
            throw new AccessDeniedException("Only ADMIN or HR can update project groups");
        }

        ProjectGroup g = projectGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", id));
        if (!g.isActive()) {
            throw new ResourceNotFoundException("ProjectGroup", id);
        }

        if (dto.getName() != null) {
            g.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            g.setDescription(dto.getDescription());
        }
        if (dto.getStartDate() != null) {
            g.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            g.setEndDate(dto.getEndDate());
        }
        if (dto.getStatus() != null) {
            g.setStatus(dto.getStatus());
        }

        return toDto(projectGroupRepository.save(g));
    }

    @Transactional
    public ProjectGroupDto addIntern(Long groupId, Long internId, Long requesterId) {
        ProjectGroup g = loadGroupWithInterns(groupId);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        requireGroupSupervisorOrAdmin(g, requester);

        User intern = userRepository.findById(internId)
                .orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("User must have role INTERN");
        }
        if (projectGroupRepository.existsByActiveTrueAndStatusInAndInterns_Id(Set.of(GroupStatus.ACTIVE), internId)) {
            throw new BadRequestException("An intern cannot belong to two active groups at the same time");
        }
        boolean exists = g.getInterns().stream().anyMatch(u -> u.getId().equals(internId));
        if (exists) {
            throw new BadRequestException("Intern is already in this group");
        }
        g.getInterns().add(intern);
        ProjectGroup saved = projectGroupRepository.save(g);
        emailService.sendCohortEnrollmentEmails(intern, saved.getSupervisor(), saved);
        return toDto(saved);
    }

    @Transactional
    public ProjectGroupDto removeIntern(Long groupId, Long internId, Long requesterId) {
        ProjectGroup g = loadGroupWithInterns(groupId);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        requireGroupSupervisorOrAdmin(g, requester);

        boolean removed = g.getInterns().removeIf(u -> u.getId().equals(internId));
        if (!removed) {
            throw new BadRequestException("Intern is not in this group");
        }
        return toDto(projectGroupRepository.save(g));
    }

    @Transactional
    public ProjectGroupDto assignSupervisor(Long groupId, Long supervisorId, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        if (requester.getRole() != Role.ADMIN && requester.getRole() != Role.HR) {
            throw new AccessDeniedException("Only ADMIN or HR can assign a group supervisor");
        }

        ProjectGroup g = projectGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", groupId));
        if (!g.isActive()) {
            throw new ResourceNotFoundException("ProjectGroup", groupId);
        }

        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", supervisorId));
        if (supervisor.getRole() != Role.SUPERVISOR) {
            throw new BadRequestException("supervisorId must reference a user with role SUPERVISOR");
        }
        g.setSupervisor(supervisor);
        return toDto(projectGroupRepository.save(g));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listInterns(Long groupId, Long requesterId) {
        ProjectGroup g = loadGroupWithInterns(groupId);
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        if (requester.getRole() == Role.ADMIN || requester.getRole() == Role.HR) {
            // ok
        } else if (requester.getRole() == Role.SUPERVISOR
                && g.getSupervisor() != null
                && Objects.equals(g.getSupervisor().getId(), requesterId)) {
            // ok
        } else {
            throw new AccessDeniedException("Not allowed to list interns for this group");
        }
        return g.getInterns().stream().map(this::toUserSummary).toList();
    }

    @Transactional
    public void softDelete(Long groupId, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        if (requester.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN can delete project groups");
        }
        ProjectGroup g = projectGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", groupId));
        g.setActive(false);
        g.setStatus(GroupStatus.CANCELLED);
        projectGroupRepository.save(g);
        log.info("Soft-deleted project group id={}", groupId);
    }

    /** Used by {@link ProjectService} — loads group with interns for validation. */
    @Transactional(readOnly = true)
    public ProjectGroup getActiveGroupWithInterns(Long groupId) {
        return projectGroupRepository.findByIdAndActiveTrueWithInterns(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", groupId));
    }

    private ProjectGroup loadGroupWithInterns(Long groupId) {
        return projectGroupRepository.findByIdAndActiveTrueWithInterns(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", groupId));
    }

    private void authorizeRead(ProjectGroup g, Long requesterId) {
        User u = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));
        if (u.getRole() == Role.ADMIN || u.getRole() == Role.HR) {
            return;
        }
        if (u.getRole() == Role.SUPERVISOR
                && g.getSupervisor() != null
                && Objects.equals(g.getSupervisor().getId(), requesterId)) {
            return;
        }
        throw new AccessDeniedException("Not allowed to view this project group");
    }

    private void requireGroupSupervisorOrAdmin(ProjectGroup g, User requester) {
        if (requester.getRole() == Role.ADMIN || requester.getRole() == Role.HR) {
            return;
        }
        if (requester.getRole() == Role.SUPERVISOR
                && g.getSupervisor() != null
                && Objects.equals(g.getSupervisor().getId(), requester.getId())) {
            return;
        }
        throw new AccessDeniedException("Only the group supervisor, HR, or an admin can manage group interns");
    }

    private ProjectGroupDto toDto(ProjectGroup g) {
        User sup = g.getSupervisor();
        String supName = sup != null
                ? sup.getFirstName() + " " + (sup.getLastName() == null ? "" : sup.getLastName()).trim()
                : null;
        int count = g.getInterns() == null ? 0 : g.getInterns().size();
        return ProjectGroupDto.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .startDate(g.getStartDate())
                .endDate(g.getEndDate())
                .status(g.getStatus())
                .supervisorId(sup != null ? sup.getId() : null)
                .supervisorName(supName)
                .internCount(count)
                .createdAt(g.getCreatedAt())
                .build();
    }

    private UserSummaryDto toUserSummary(User u) {
        return UserSummaryDto.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .universityId(u.getUniversityId())
                .profilePhotoUrl(u.getProfilePhotoUrl())
                .role(u.getRole().name())
                .build();
    }
}
