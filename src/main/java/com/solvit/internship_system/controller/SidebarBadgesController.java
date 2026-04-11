package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.SidebarBadgesDto;
import com.solvit.internship_system.entity.LeaveRequest;
import com.solvit.internship_system.repository.LeaveRequestRepository;
import com.solvit.internship_system.repository.MessageRepository;
import com.solvit.internship_system.repository.NotificationRepository;
import com.solvit.internship_system.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sidebar")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@org.springframework.web.bind.annotation.CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class SidebarBadgesController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final JwtUtil jwtUtil;

    @GetMapping("/badges")
    public ResponseEntity<SidebarBadgesDto> getBadges(@RequestHeader("Authorization") String authHeader) {
        long pendingLeave = leaveRequestRepository.findByStatusOrderByCreatedAtDesc(
                LeaveRequest.LeaveStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 1))
                .getTotalElements();
        Long userId = null;
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
            }
        } catch (Exception ignored) {}
        int unreadMessages = userId != null ? (int) messageRepository.countByReceiver_IdAndReadAtIsNull(userId) : 0;
        int unreadNotifications = userId != null ? (int) notificationRepository.countByUser_IdAndReadAtIsNull(userId) : 0;
        return ResponseEntity.ok(new SidebarBadgesDto((int) pendingLeave, unreadMessages, unreadNotifications));
    }
}
