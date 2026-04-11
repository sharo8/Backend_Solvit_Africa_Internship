package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.ActionPlan;
import com.solvit.internship_system.entity.Feedback;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.ActionPlanRepository;
import com.solvit.internship_system.repository.FeedbackRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionPlanService {

    private final ActionPlanRepository actionPlanRepository;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;

    @Transactional
    public ActionPlan create(Long internId, Long feedbackId, ActionPlan plan) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        plan.setIntern(intern);
        if (feedbackId != null) {
            Feedback f = feedbackRepository.findById(feedbackId).orElse(null);
            plan.setFeedback(f);
        }
        plan.setStatus(ActionPlan.ActionPlanStatus.PENDING);
        return actionPlanRepository.save(plan);
    }

    public List<ActionPlan> getByIntern(Long internId) {
        return actionPlanRepository.findByIntern_IdOrderByCreatedAtDesc(internId);
    }

    public ActionPlan getById(Long id) {
        return actionPlanRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ActionPlan", id));
    }

    @Transactional
    public ActionPlan updateStatus(Long id, ActionPlan.ActionPlanStatus status) {
        ActionPlan p = getById(id);
        p.setStatus(status);
        return actionPlanRepository.save(p);
    }
}
