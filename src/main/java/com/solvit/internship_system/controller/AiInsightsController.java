package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.ai.*;
import com.solvit.internship_system.service.AiInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class AiInsightsController {

    private final AiInsightsService aiInsightsService;

    @GetMapping("/summary")
    public ResponseEntity<AiInsightsSummaryDto> summary() {
        return ResponseEntity.ok(aiInsightsService.buildSummary());
    }

    @GetMapping("/feedback-trend")
    public ResponseEntity<List<FeedbackTrendPointDto>> feedbackTrend(
            @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(aiInsightsService.feedbackTrendLastMonths(Math.min(months, 24)));
    }

    @GetMapping("/at-risk")
    public ResponseEntity<List<AtRiskInternBriefDto>> atRisk() {
        return ResponseEntity.ok(aiInsightsService.listAtRiskBriefs());
    }

    @GetMapping("/peer-comparison/{internId}")
    public ResponseEntity<PeerComparisonDto> peer(@PathVariable Long internId) {
        return ResponseEntity.ok(aiInsightsService.compareInternToPeers(internId));
    }

    @GetMapping("/performance-evaluation/{internId}")
    public ResponseEntity<AiPerformanceEvaluationDto> performanceEvaluation(@PathVariable Long internId) {
        return ResponseEntity.ok(aiInsightsService.evaluateInternPerformance(internId));
    }

    @GetMapping("/performance-evaluations")
    public ResponseEntity<List<AiPerformanceEvaluationDto>> performanceEvaluations() {
        return ResponseEntity.ok(aiInsightsService.evaluateAllActiveInterns());
    }

    /** Recomputes monthly scores, peer percentiles, and at-risk flags for all interns. */
    @PostMapping("/recompute-scores")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> recompute() {
        int n = aiInsightsService.recomputePerformanceScoresForAllInterns();
        return ResponseEntity.ok(Map.of("internsUpdated", n));
    }
}
