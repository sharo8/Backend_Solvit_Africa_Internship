package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.dashboard.*;
import com.solvit.internship_system.security.CurrentUserPrincipal;
import com.solvit.internship_system.service.SupervisorDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supervisor/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISOR')")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class SupervisorDashboardController {

    private final SupervisorDashboardService supervisorDashboardService;

    @GetMapping("/kpis")
    public ResponseEntity<AdminKpisDto> getKpis(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getKpis(principal.getUserId()));
    }

    @GetMapping("/attendance-trend")
    public ResponseEntity<List<AttendanceTrendDto>> getAttendanceTrend(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getAttendanceTrend(days, principal.getUserId()));
    }

    @GetMapping("/performance-by-cohort")
    public ResponseEntity<List<PerformanceByCohortDto>> getPerformanceByCohort(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getPerformanceByCohort(principal.getUserId()));
    }

    @GetMapping("/task-completion-weekly")
    public ResponseEntity<List<TaskCompletionWeeklyDto>> getTaskCompletionWeekly(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getTaskCompletionWeekly(principal.getUserId()));
    }

    @GetMapping("/skill-distribution")
    public ResponseEntity<List<SkillDistributionDto>> getSkillDistribution(
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getSkillDistribution(principal.getUserId()));
    }

    @GetMapping("/attendance-histogram")
    public ResponseEntity<List<AttendanceHistogramDto>> getAttendanceHistogram(
            @RequestParam int month,
            @RequestParam int year,
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getAttendanceHistogram(month, year, principal.getUserId()));
    }

    @GetMapping("/monthly-registrations")
    public ResponseEntity<List<MonthlyRegistrationsDto>> getMonthlyRegistrations(
            @RequestParam(defaultValue = "2026") int year,
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getMonthlyRegistrations(year, principal.getUserId()));
    }

    @GetMapping("/feedback-scores-trend")
    public ResponseEntity<List<FeedbackScoresTrendDto>> getFeedbackScoresTrend(
            @RequestParam(defaultValue = "8") int weeks,
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(supervisorDashboardService.getFeedbackScoresTrend(weeks, principal.getUserId()));
    }
}
