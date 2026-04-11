package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.Feedback;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.FeedbackService;
import com.solvit.internship_system.service.FeedbackSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.solvit.internship_system.dto.feedback.FeedbackSummaryDto;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final FeedbackSummaryService feedbackSummaryService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<Feedback> create(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody Feedback feedback) {
        Long supervisorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Long internId = feedback.getIntern() != null ? feedback.getIntern().getId() : null;
        if (internId == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(feedbackService.create(internId, supervisorId, feedback));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<Page<Feedback>> getMyFeedbacks(@RequestHeader("Authorization") String authHeader,
                                                         Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(feedbackService.getByIntern(userId, pageable));
    }

    @GetMapping("/sent")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<Page<Feedback>> getMySentFeedbacks(@RequestHeader("Authorization") String authHeader,
                                                             Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(feedbackService.getBySupervisor(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Feedback> getById(@RequestHeader("Authorization") String authHeader,
                                            @PathVariable Long id) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(feedbackService.getByIdForActor(id, userId));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<Feedback> acknowledge(@RequestHeader("Authorization") String authHeader,
                                                 @PathVariable Long id) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(feedbackService.acknowledge(id, userId));
    }

    @GetMapping("/history/received")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<Page<Feedback>> getFeedbackReceivedBySupervisor(
            @RequestHeader("Authorization") String authHeader,
            Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(feedbackService.getBySupervisor(userId, pageable));
    }

    @GetMapping("/summary/{internId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN','HR')")
    public ResponseEntity<FeedbackSummaryDto> summary(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long internId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long actorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        LocalDate start = from != null ? LocalDate.parse(from) : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? LocalDate.parse(to) : LocalDate.now();
        return ResponseEntity.ok(feedbackSummaryService.generateSummary(internId, start, end, actorId));
    }

    @GetMapping("/summary/me")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<FeedbackSummaryDto> mySummary(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long actorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        LocalDate start = from != null ? LocalDate.parse(from) : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? LocalDate.parse(to) : LocalDate.now();
        return ResponseEntity.ok(feedbackSummaryService.generateSummary(actorId, start, end, actorId));
    }

    @GetMapping("/summary/{internId}/export/pdf")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN','HR')")
    public ResponseEntity<byte[]> exportSummaryPdf(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long internId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long actorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        LocalDate start = from != null ? LocalDate.parse(from) : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? LocalDate.parse(to) : LocalDate.now();
        FeedbackSummaryDto summary = feedbackSummaryService.generateSummary(internId, start, end, actorId);
        byte[] pdf = feedbackSummaryService.exportSummaryPdf(summary);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"feedback-summary-" + internId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/summary/me/export/pdf")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<byte[]> exportMySummaryPdf(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long actorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        LocalDate start = from != null ? LocalDate.parse(from) : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? LocalDate.parse(to) : LocalDate.now();
        FeedbackSummaryDto summary = feedbackSummaryService.generateSummary(actorId, start, end, actorId);
        byte[] pdf = feedbackSummaryService.exportSummaryPdf(summary);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-feedback-summary.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
