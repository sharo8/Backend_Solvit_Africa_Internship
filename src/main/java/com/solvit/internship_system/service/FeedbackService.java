package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Feedback;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ConflictException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.FeedbackRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final NotificationService notificationService;
    private final AiInsightsService aiInsightsService;

    @Transactional
    public Feedback create(Long internId, Long supervisorId, Feedback feedback) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        User supervisor = userRepository.findById(supervisorId).orElseThrow(() -> new ResourceNotFoundException("User", supervisorId));
        if (supervisor.getRole() != Role.SUPERVISOR && supervisor.getRole() != Role.ADMIN) {
            throw new BadRequestException("Only supervisor/admin can create feedback");
        }
        if (supervisor.getRole() == Role.SUPERVISOR) {
            boolean belongs = internProfileRepository.findByUser_Id(internId)
                    .map(p -> supervisorId.equals(p.getSupervisorUserId()))
                    .orElse(false);
            if (!belongs) {
                throw new ConflictException("This intern is not under your supervision");
            }
        }
        feedback.setIntern(intern);
        feedback.setSupervisor(supervisor);
        aiInsightsService.applySentimentToFeedback(feedback);
        Feedback saved = feedbackRepository.save(feedback);
        notificationService.create(internId, "New Feedback", "You received feedback from your supervisor.",
                com.solvit.internship_system.entity.Notification.NotificationType.FEEDBACK_RECEIVED, "Feedback", saved.getId(), true);
        return saved;
    }

    public Feedback getById(Long id) {
        return feedbackRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Feedback", id));
    }

    public Feedback getByIdForActor(Long feedbackId, Long actorId) {
        Feedback f = getById(feedbackId);
        User actor = userRepository.findById(actorId).orElseThrow(() -> new ResourceNotFoundException("User", actorId));
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR) {
            return f;
        }
        if (actor.getRole() == Role.INTERN && f.getIntern() != null && actorId.equals(f.getIntern().getId())) {
            return f;
        }
        if (actor.getRole() == Role.SUPERVISOR && f.getSupervisor() != null && actorId.equals(f.getSupervisor().getId())) {
            return f;
        }
        throw new ResourceNotFoundException("Feedback", feedbackId);
    }

    @Transactional(readOnly = true)
    public Page<Feedback> getByIntern(Long internId, Pageable pageable) {
        return feedbackRepository.findByIntern_IdOrderByCreatedAtDesc(internId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Feedback> getBySupervisor(Long supervisorId, Pageable pageable) {
        return feedbackRepository.findBySupervisor_IdOrderByCreatedAtDesc(supervisorId, pageable);
    }

    @Transactional
    public Feedback acknowledge(Long feedbackId, Long userId) {
        Feedback f = getById(feedbackId);
        if (!f.getIntern().getId().equals(userId)) throw new ResourceNotFoundException("Feedback", feedbackId);
        f.setAcknowledged(true);
        f.setAcknowledgedAt(Instant.now());
        return feedbackRepository.save(f);
    }
}
