package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.*;
import com.solvit.internship_system.security.CurrentUserResolver;
import com.solvit.internship_system.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AiV1Controller {
    private final CurrentUserResolver currentUserResolver;
    private final InternRecordService internRecordService;
    private final EvaluationFormService evaluationFormService;
    private final InternTaskAiService internTaskAiService;
    private final AiPerformanceScoreRecordService aiPerformanceScoreRecordService;

    @GetMapping("/interns")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<List<InternRecord>> listInterns(
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(required = false) InternRecord.InternStatus status
    ) {
        Role role = currentUserResolver.requireRole();
        Long uid = currentUserResolver.requireUserId();
        if (role == Role.SUPERVISOR) {
            return ResponseEntity.ok(internRecordService.listBySupervisor(uid));
        }
        List<InternRecord> all = supervisorId != null
                ? internRecordService.listBySupervisor(supervisorId)
                : internRecordService.listAll();
        if (status != null) {
            all = all.stream().filter(i -> i.getStatus() == status).toList();
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/interns/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<InternRecord> getIntern(@PathVariable Long id) {
        return ResponseEntity.ok(internRecordService.getById(id));
    }

    @PostMapping("/interns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InternRecord> createIntern(@RequestBody InternRecord body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(internRecordService.create(body));
    }

    @PutMapping("/interns/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<InternRecord> updateIntern(@PathVariable Long id, @RequestBody InternRecord body) {
        return ResponseEntity.ok(internRecordService.update(id, body));
    }

    @DeleteMapping("/interns/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteIntern(@PathVariable Long id) {
        internRecordService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Intern suspended"));
    }

    @GetMapping("/evaluations")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<List<EvaluationForm>> listEvaluationForms(@RequestParam Long internId) {
        return ResponseEntity.ok(evaluationFormService.listForIntern(internId));
    }

    @GetMapping("/evaluations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR','INTERN')")
    public ResponseEntity<EvaluationForm> getEvaluationForm(@PathVariable Long id) {
        return ResponseEntity.ok(evaluationFormService.getById(id));
    }

    @PostMapping("/evaluations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<EvaluationForm> createEvaluationDraft(@RequestBody EvaluationForm body) {
        Long supervisorId = currentUserResolver.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationFormService.saveDraft(body, supervisorId));
    }

    @PutMapping("/evaluations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<EvaluationForm> updateEvaluationDraft(@PathVariable Long id, @RequestBody EvaluationForm body) {
        return ResponseEntity.ok(evaluationFormService.updateDraft(id, body));
    }

    @PostMapping("/evaluations/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<EvaluationForm> submitEvaluation(@PathVariable Long id) {
        return ResponseEntity.ok(evaluationFormService.submit(id));
    }

    @GetMapping("/interns/{id}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR','INTERN')")
    public ResponseEntity<List<InternTask>> listInternTasks(@PathVariable Long id) {
        return ResponseEntity.ok(internTaskAiService.listForIntern(id));
    }

    @PostMapping("/interns/{id}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<InternTask> createInternTask(@PathVariable Long id, @RequestBody InternTask body) {
        Long supervisorId = currentUserResolver.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(internTaskAiService.assignTask(id, supervisorId, body));
    }

    @PostMapping("/tasks/{id}/rate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<InternTask> rateTask(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        BigDecimal rating = body.get("qualityRating") == null
                ? null
                : new BigDecimal(String.valueOf(body.get("qualityRating")));
        String comment = body.get("qualityComment") == null ? null : String.valueOf(body.get("qualityComment"));
        return ResponseEntity.ok(internTaskAiService.rateQuality(id, rating, comment));
    }

    @PostMapping("/tasks/{id}/submit")
    @PreAuthorize("hasAnyRole('INTERN','ADMIN','SUPERVISOR')")
    public ResponseEntity<InternTask> submitTask(@PathVariable Long id) {
        return ResponseEntity.ok(internTaskAiService.updateStatus(id, InternTask.TaskStatus.SUBMITTED));
    }

    @PostMapping("/tasks/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<InternTask> approveTask(@PathVariable Long id) {
        return ResponseEntity.ok(internTaskAiService.updateStatus(id, InternTask.TaskStatus.APPROVED));
    }

    @PostMapping("/tasks/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<InternTask> rejectTask(@PathVariable Long id) {
        return ResponseEntity.ok(internTaskAiService.updateStatus(id, InternTask.TaskStatus.REJECTED));
    }

    @GetMapping("/ai/scores/{internId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR','INTERN')")
    public ResponseEntity<AiPerformanceScoreRecord> latestScore(@PathVariable Long internId) {
        List<AiPerformanceScoreRecord> history = aiPerformanceScoreRecordService.history(internId);
        return ResponseEntity.ok(history.isEmpty() ? null : history.get(0));
    }

    @GetMapping("/ai/scores/{internId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR','INTERN')")
    public ResponseEntity<List<AiPerformanceScoreRecord>> scoreHistory(
            @PathVariable Long internId,
            @RequestParam(defaultValue = "12") int weeks
    ) {
        List<AiPerformanceScoreRecord> history = aiPerformanceScoreRecordService.history(internId);
        return ResponseEntity.ok(history.stream().limit(Math.max(1, weeks)).toList());
    }

    @PostMapping("/ai/scores/{internId}/compute")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<AiPerformanceScoreRecord> computeScore(@PathVariable Long internId) {
        return ResponseEntity.ok(aiPerformanceScoreRecordService.computeAndStore(internId));
    }
}
