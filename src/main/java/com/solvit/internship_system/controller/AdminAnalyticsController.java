package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.analytics.*;
import com.solvit.internship_system.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/users/by-role")
    public ResponseEntity<List<UsersByRoleDto>> getUsersByRole() {
        return ResponseEntity.ok(adminAnalyticsService.getUsersByRole());
    }

    @GetMapping("/users/growth")
    public ResponseEntity<List<UserGrowthDto>> getUserGrowth(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(adminAnalyticsService.getUserGrowth(months));
    }

    @GetMapping("/attendance/daily")
    public ResponseEntity<List<AttendanceDailyDto>> getAttendanceDaily(@RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(adminAnalyticsService.getAttendanceDaily(days));
    }

    @GetMapping("/attendance/rate-distribution")
    public ResponseEntity<List<AttendanceRateDistributionDto>> getAttendanceRateDistribution() {
        return ResponseEntity.ok(adminAnalyticsService.getAttendanceRateDistribution());
    }

    @GetMapping("/tasks/status-summary")
    public ResponseEntity<List<TaskStatusSummaryDto>> getTaskStatusSummary() {
        return ResponseEntity.ok(adminAnalyticsService.getTaskStatusSummary());
    }

    @GetMapping("/tasks/weekly-trend")
    public ResponseEntity<List<TaskWeeklyTrendDto>> getTaskWeeklyTrend(@RequestParam(defaultValue = "6") int weeks) {
        return ResponseEntity.ok(adminAnalyticsService.getTaskWeeklyTrend(weeks));
    }

    @GetMapping("/performance/score-distribution")
    public ResponseEntity<List<ScoreDistributionDto>> getPerformanceScoreDistribution() {
        return ResponseEntity.ok(adminAnalyticsService.getPerformanceScoreDistribution());
    }

    @GetMapping("/performance/by-supervisor")
    public ResponseEntity<List<PerformanceBySupervisorDto>> getPerformanceBySupervisor() {
        return ResponseEntity.ok(adminAnalyticsService.getPerformanceBySupervisor());
    }

    @GetMapping("/feedback/scores-by-type")
    public ResponseEntity<List<FeedbackScoresByTypeDto>> getFeedbackScoresByType() {
        return ResponseEntity.ok(adminAnalyticsService.getFeedbackScoresByType());
    }

    @GetMapping("/feedback/monthly-avg")
    public ResponseEntity<List<FeedbackMonthlyAvgDto>> getFeedbackMonthlyAvg(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(adminAnalyticsService.getFeedbackMonthlyAvg(months));
    }

    @GetMapping("/leave/by-type")
    public ResponseEntity<List<LeaveByTypeDto>> getLeaveByType() {
        return ResponseEntity.ok(adminAnalyticsService.getLeaveByType());
    }

    @GetMapping("/leave/status-monthly")
    public ResponseEntity<List<LeaveStatusMonthlyDto>> getLeaveStatusMonthly(@RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(adminAnalyticsService.getLeaveStatusMonthly(months));
    }

    @GetMapping("/learning/completion-by-path")
    public ResponseEntity<List<LearningCompletionByPathDto>> getLearningCompletionByPath() {
        return ResponseEntity.ok(adminAnalyticsService.getLearningCompletionByPath());
    }

    @GetMapping("/audit/actions-by-type")
    public ResponseEntity<List<AuditActionsByTypeDto>> getAuditActionsByType() {
        return ResponseEntity.ok(adminAnalyticsService.getAuditActionsByType());
    }

    @GetMapping("/audit/daily-activity")
    public ResponseEntity<List<AuditDailyActivityDto>> getAuditDailyActivity(@RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(adminAnalyticsService.getAuditDailyActivity(days));
    }
}
