package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.ActionPlan;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.ActionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/action-plans")
@RequiredArgsConstructor
public class ActionPlanController {

    private final ActionPlanService actionPlanService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<ActionPlan> create(@RequestHeader("Authorization") String authHeader,
                                             @RequestBody ActionPlan plan) {
        Long internId = plan.getIntern() != null ? plan.getIntern().getId() : null;
        Long feedbackId = plan.getFeedback() != null ? plan.getFeedback().getId() : null;
        if (internId == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(actionPlanService.create(internId, feedbackId, plan));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ActionPlan>> getMyPlans(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(actionPlanService.getByIntern(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActionPlan> getById(@PathVariable Long id) {
        return ResponseEntity.ok(actionPlanService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ActionPlan> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        ActionPlan.ActionPlanStatus status = ActionPlan.ActionPlanStatus.valueOf(statusStr);
        return ResponseEntity.ok(actionPlanService.updateStatus(id, status));
    }
}
