package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.evaluation.AcknowledgeEvaluationRequestDto;
import com.solvit.internship_system.dto.evaluation.CreateEvaluationRequestDto;
import com.solvit.internship_system.dto.evaluation.EvaluationDto;
import com.solvit.internship_system.dto.evaluation.UpdateEvaluationDraftDto;
import com.solvit.internship_system.entity.Evaluation;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.LocalDate;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<EvaluationDto> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateEvaluationRequestDto dto) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.create(dto, uid));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<Page<EvaluationDto>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long internId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Evaluation.EvaluationType type,
            @RequestParam(required = false) Evaluation.EvaluationStatus status,
            Pageable pageable) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(evaluationService.list(internId, groupId, type, status, pageable, uid));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<Page<EvaluationDto>> myEvaluations(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(evaluationService.listForIntern(userId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EvaluationDto> getById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(evaluationService.getById(id, uid));
    }

    @GetMapping("/attendance-score")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<Map<String, Object>> suggestedAttendanceScore(
            @RequestParam Long internId,
            @RequestParam(required = false) LocalDate evaluationDate,
            @RequestParam(defaultValue = "30") int lookbackDays) {
        Integer score = evaluationService.suggestAttendanceScore(internId, evaluationDate, lookbackDays);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("internId", internId);
        out.put("evaluationDate", evaluationDate);
        out.put("lookbackDays", lookbackDays);
        out.put("attendanceScore", score);
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<EvaluationDto> updateDraft(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody UpdateEvaluationDraftDto dto) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(evaluationService.updateDraft(id, dto, uid));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<EvaluationDto> submit(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(evaluationService.submit(id, uid));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<EvaluationDto> acknowledge(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody AcknowledgeEvaluationRequestDto dto) {
        Long internId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(evaluationService.acknowledge(id, internId, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        evaluationService.softDelete(id, uid);
        return ResponseEntity.ok(Map.of("message", "Evaluation deleted"));
    }
}
