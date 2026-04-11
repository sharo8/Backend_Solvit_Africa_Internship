package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.PerformanceScore;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.PerformanceScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/performance-scores")
@RequiredArgsConstructor
public class PerformanceScoreController {

    private final PerformanceScoreService performanceScoreService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<PerformanceScore> create(@RequestHeader("Authorization") String authHeader,
                                                   @RequestBody PerformanceScore score) {
        Long internId = score.getIntern() != null ? score.getIntern().getId() : null;
        if (internId == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(performanceScoreService.create(internId, score));
    }

    @GetMapping("/me")
    public ResponseEntity<List<PerformanceScore>> getMyScores(@RequestHeader("Authorization") String authHeader,
                                                              @RequestParam(defaultValue = "10") int limit) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(performanceScoreService.getByIntern(userId, limit));
    }

    @GetMapping("/at-risk")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR')")
    public ResponseEntity<List<PerformanceScore>> getAtRisk() {
        return ResponseEntity.ok(performanceScoreService.getAtRisk());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PerformanceScore> getById(@PathVariable Long id) {
        return ResponseEntity.ok(performanceScoreService.getById(id));
    }
}
