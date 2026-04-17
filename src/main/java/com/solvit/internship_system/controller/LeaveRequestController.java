package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.LeaveRequest;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<LeaveRequest> create(@RequestHeader("Authorization") String authHeader,
                                                @RequestBody LeaveRequest request) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(leaveRequestService.create(userId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<LeaveRequest>> getMyLeaves(@RequestHeader("Authorization") String authHeader,
                                                          Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(leaveRequestService.getByUser(userId, pageable));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Page<LeaveRequest>> listForStaff(@RequestParam(required = false) String status,
                                                           Pageable pageable) {
        if (status == null || status.isBlank()) {
            return ResponseEntity.ok(leaveRequestService.getAll(pageable));
        }
        try {
            LeaveRequest.LeaveStatus s = LeaveRequest.LeaveStatus.valueOf(status.trim().toUpperCase());
            return ResponseEntity.ok(leaveRequestService.getByStatus(s, pageable));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid status: " + status);
        }
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<Page<LeaveRequest>> getPending(Pageable pageable) {
        return ResponseEntity.ok(leaveRequestService.getPending(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveRequest> getById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveRequestService.getById(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<LeaveRequest> approve(@RequestHeader("Authorization") String authHeader,
                                                @PathVariable Long id) {
        Long approverId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(leaveRequestService.approve(id, approverId));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<LeaveRequest> reject(@RequestHeader("Authorization") String authHeader,
                                               @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Long approverId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(leaveRequestService.reject(id, approverId, reason));
    }
}
