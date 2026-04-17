package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.EvaluationForm;
import com.solvit.internship_system.entity.InternRecord;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.EvaluationFormRepository;
import com.solvit.internship_system.repository.InternRecordRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationFormService {
    private final EvaluationFormRepository evaluationFormRepository;
    private final InternRecordRepository internRecordRepository;
    private final UserRepository userRepository;

    @Transactional
    public EvaluationForm saveDraft(EvaluationForm form, Long supervisorUserId) {
        if (form == null || form.getIntern() == null || form.getIntern().getId() == null) {
            throw new BadRequestException("intern_id is required");
        }
        InternRecord intern = internRecordRepository.findById(form.getIntern().getId())
                .orElseThrow(() -> new ResourceNotFoundException("InternRecord", form.getIntern().getId()));
        User supervisor = userRepository.findById(supervisorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", supervisorUserId));
        form.setIntern(intern);
        form.setSupervisor(supervisor);
        form.setStatus(EvaluationForm.FormStatus.DRAFT);
        return evaluationFormRepository.save(form);
    }

    @Transactional
    public EvaluationForm submit(Long formId) {
        EvaluationForm form = evaluationFormRepository.findById(formId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationForm", formId));
        if (form.getStatus() != EvaluationForm.FormStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT forms can be submitted");
        }
        form.setStatus(EvaluationForm.FormStatus.SUBMITTED);
        form.setSubmissionDate(Instant.now());
        return evaluationFormRepository.save(form);
    }

    @Transactional(readOnly = true)
    public List<EvaluationForm> listForIntern(Long internRecordId) {
        return evaluationFormRepository.findByIntern_IdOrderByCreatedAtDesc(internRecordId);
    }

    @Transactional(readOnly = true)
    public EvaluationForm getById(Long formId) {
        return evaluationFormRepository.findById(formId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationForm", formId));
    }

    @Transactional
    public EvaluationForm updateDraft(Long formId, EvaluationForm patch) {
        EvaluationForm form = evaluationFormRepository.findById(formId)
                .orElseThrow(() -> new ResourceNotFoundException("EvaluationForm", formId));
        if (form.getStatus() != EvaluationForm.FormStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT forms can be updated");
        }
        if (patch.getAttendanceRating() != null) form.setAttendanceRating(patch.getAttendanceRating());
        if (patch.getTaskCompletionRating() != null) form.setTaskCompletionRating(patch.getTaskCompletionRating());
        if (patch.getWorkQualityRating() != null) form.setWorkQualityRating(patch.getWorkQualityRating());
        if (patch.getTechnicalSkillsRating() != null) form.setTechnicalSkillsRating(patch.getTechnicalSkillsRating());
        if (patch.getConductEngagementRating() != null) form.setConductEngagementRating(patch.getConductEngagementRating());
        if (patch.getAttendanceComment() != null) form.setAttendanceComment(patch.getAttendanceComment());
        if (patch.getTaskCompletionComment() != null) form.setTaskCompletionComment(patch.getTaskCompletionComment());
        if (patch.getWorkQualityComment() != null) form.setWorkQualityComment(patch.getWorkQualityComment());
        if (patch.getTechnicalSkillsComment() != null) form.setTechnicalSkillsComment(patch.getTechnicalSkillsComment());
        if (patch.getConductComment() != null) form.setConductComment(patch.getConductComment());
        if (patch.getGeneralComment() != null) form.setGeneralComment(patch.getGeneralComment());
        return evaluationFormRepository.save(form);
    }
}
