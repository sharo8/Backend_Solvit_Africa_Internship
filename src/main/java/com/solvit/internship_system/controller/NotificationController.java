package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.Notification;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<Page<Notification>> getMyNotifications(@RequestHeader("Authorization") String authHeader,
                                                                 Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(notificationService.getByUser(userId, pageable));
    }

    @GetMapping("/unread")
    public ResponseEntity<Page<Notification>> getUnread(@RequestHeader("Authorization") String authHeader,
                                                        Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(notificationService.getUnreadByUser(userId, pageable));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable Long id) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("markedCount", count));
    }
}
