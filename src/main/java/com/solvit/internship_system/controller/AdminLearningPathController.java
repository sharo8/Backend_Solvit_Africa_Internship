package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.learning.LearningPathRecommendationDto;
import com.solvit.internship_system.entity.LearningPath;
import com.solvit.internship_system.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/learning-paths")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class AdminLearningPathController {

    private final LearningPathService learningPathService;

    @GetMapping
    public ResponseEntity<Page<LearningPath>> list(Pageable pageable) {
        return ResponseEntity.ok(learningPathService.listAll(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<LearningPath> createForIntern(@RequestBody LearningPath body,
                                                        @RequestParam Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(learningPathService.createForIntern(userId, body));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        learningPathService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recommendations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LearningPathRecommendationDto>> recommendations(@RequestParam Long userId) {
        return ResponseEntity.ok(learningPathService.recommendForIntern(userId));
    }

    @PostMapping("/auto-assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> autoAssign(@RequestParam Long userId) {
        int n = learningPathService.autoAssignRecommendedForIntern(userId);
        return ResponseEntity.ok(Map.of("assigned", n));
    }
}
