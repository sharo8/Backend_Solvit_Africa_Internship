package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.LearningPath;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning-paths")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService learningPathService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<LearningPath> create(@RequestHeader("Authorization") String authHeader,
                                              @RequestBody LearningPath path) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(learningPathService.create(userId, path));
    }

    @GetMapping("/me")
    public ResponseEntity<List<LearningPath>> getMyPaths(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(learningPathService.getByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LearningPath> getById(@PathVariable Long id) {
        return ResponseEntity.ok(learningPathService.getById(id));
    }

    @PatchMapping("/{id}/progress")
    public ResponseEntity<LearningPath> updateProgress(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer progress = body.get("progressPercent");
        return ResponseEntity.ok(learningPathService.updateProgress(id, progress));
    }
}
