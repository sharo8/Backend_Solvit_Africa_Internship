package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.AuditLog;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final JwtUtil jwtUtil;

    @GetMapping("/me")
    public ResponseEntity<Page<AuditLog>> getMyAuditLogs(@RequestHeader("Authorization") String authHeader,
                                                          Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(auditService.getByUser(userId, pageable));
    }

    @GetMapping("/action/{action}")
    public ResponseEntity<Page<AuditLog>> getByAction(@PathVariable String action, Pageable pageable) {
        return ResponseEntity.ok(auditService.getByAction(action, pageable));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<Page<AuditLog>> getByEntity(@PathVariable String entityType,
                                                       @PathVariable Long entityId, Pageable pageable) {
        return ResponseEntity.ok(auditService.getByEntity(entityType, entityId, pageable));
    }
}
