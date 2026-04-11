package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.dashboard.*;
import com.solvit.internship_system.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/kpis")
    public ResponseEntity<AdminKpisDto> getKpis() {
        return ResponseEntity.ok(adminDashboardService.getKpis());
    }

    @GetMapping("/attendance-trend")
    public ResponseEntity<List<AttendanceTrendDto>> getAttendanceTrend(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminDashboardService.getAttendanceTrend(days));
    }

    @GetMapping("/performance-by-cohort")
    public ResponseEntity<List<PerformanceByCohortDto>> getPerformanceByCohort() {
        return ResponseEntity.ok(adminDashboardService.getPerformanceByCohort());
    }

    @GetMapping("/task-completion-weekly")
    public ResponseEntity<List<TaskCompletionWeeklyDto>> getTaskCompletionWeekly() {
        return ResponseEntity.ok(adminDashboardService.getTaskCompletionWeekly());
    }

    @GetMapping("/skill-distribution")
    public ResponseEntity<List<SkillDistributionDto>> getSkillDistribution() {
        return ResponseEntity.ok(adminDashboardService.getSkillDistribution());
    }

    @GetMapping("/intern-status-distribution")
    public ResponseEntity<List<InternStatusDistributionDto>> getInternStatusDistribution() {
        return ResponseEntity.ok(adminDashboardService.getInternStatusDistribution());
    }

    @GetMapping("/attendance-histogram")
    public ResponseEntity<List<AttendanceHistogramDto>> getAttendanceHistogram(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(adminDashboardService.getAttendanceHistogram(month, year));
    }

    @GetMapping("/monthly-registrations")
    public ResponseEntity<List<MonthlyRegistrationsDto>> getMonthlyRegistrations(
            @RequestParam(defaultValue = "2026") int year) {
        return ResponseEntity.ok(adminDashboardService.getMonthlyRegistrations(year));
    }

    @GetMapping("/feedback-scores-trend")
    public ResponseEntity<List<FeedbackScoresTrendDto>> getFeedbackScoresTrend(
            @RequestParam(defaultValue = "8") int weeks) {
        return ResponseEntity.ok(adminDashboardService.getFeedbackScoresTrend(weeks));
    }

    @GetMapping("/top-performers")
    public ResponseEntity<List<TopPerformerDto>> getTopPerformers(
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(adminDashboardService.getTopPerformers(limit, sortBy, search));
    }
}
