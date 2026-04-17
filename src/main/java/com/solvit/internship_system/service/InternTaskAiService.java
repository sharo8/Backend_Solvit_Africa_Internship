package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.InternRecord;
import com.solvit.internship_system.entity.InternTask;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.InternRecordRepository;
import com.solvit.internship_system.repository.InternTaskRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternTaskAiService {
    private final InternTaskRepository internTaskRepository;
    private final InternRecordRepository internRecordRepository;
    private final UserRepository userRepository;

    @Transactional
    public InternTask assignTask(Long internRecordId, Long supervisorUserId, InternTask payload) {
        InternRecord intern = internRecordRepository.findById(internRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("InternRecord", internRecordId));
        User supervisor = userRepository.findById(supervisorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", supervisorUserId));
        if (payload == null || payload.getTitle() == null || payload.getTitle().isBlank()) {
            throw new BadRequestException("Task title is required");
        }
        payload.setIntern(intern);
        payload.setSupervisor(supervisor);
        if (payload.getAssignedDate() == null) {
            payload.setAssignedDate(LocalDate.now());
        }
        if (payload.getDueDate() == null) {
            throw new BadRequestException("due_date is required");
        }
        payload.setStatus(InternTask.TaskStatus.ASSIGNED);
        return internTaskRepository.save(payload);
    }

    @Transactional
    public InternTask updateStatus(Long internTaskId, InternTask.TaskStatus status) {
        InternTask task = internTaskRepository.findById(internTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("InternTask", internTaskId));
        task.setStatus(status);
        if (status == InternTask.TaskStatus.APPROVED) {
            task.setCompletedDate(LocalDate.now());
            task.setOnTime(task.getDueDate() == null || !task.getCompletedDate().isAfter(task.getDueDate()));
        }
        return internTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<InternTask> listForIntern(Long internRecordId) {
        return internTaskRepository.findByIntern_IdOrderByCreatedAtDesc(internRecordId);
    }

    @Transactional
    public InternTask rateQuality(Long internTaskId, java.math.BigDecimal qualityRating, String qualityComment) {
        InternTask task = internTaskRepository.findById(internTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("InternTask", internTaskId));
        if (qualityRating != null) {
            task.setQualityRating(qualityRating);
        }
        if (qualityComment != null) {
            task.setQualityComment(qualityComment.trim());
        }
        return internTaskRepository.save(task);
    }
}
