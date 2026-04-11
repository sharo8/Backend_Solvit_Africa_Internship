package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.Message;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<Message> send(@RequestHeader("Authorization") String authHeader,
                                        @RequestBody Map<String, Object> body) {
        Long senderId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Long receiverId = body.get("receiverId") != null ? ((Number) body.get("receiverId")).longValue() : null;
        String content = (String) body.get("content");
        if (receiverId == null || content == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(messageService.send(senderId, receiverId, content));
    }

    @GetMapping("/conversation/{otherUserId}")
    public ResponseEntity<Page<Message>> getConversation(@RequestHeader("Authorization") String authHeader,
                                                         @PathVariable Long otherUserId, Pageable pageable) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(messageService.getConversation(userId, otherUserId, pageable));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(Map.of("count", messageService.countUnread(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable Long id) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        messageService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<Map<String, Object>>> contacts(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(
                messageService.getAllowedContacts(userId).stream()
                        .map(u -> {
                            Map<String, Object> m = new java.util.HashMap<>();
                            m.put("id", u.getId());
                            m.put("firstName", u.getFirstName());
                            m.put("lastName", u.getLastName());
                            m.put("role", u.getRole().name());
                            m.put("profilePhotoUrl", u.getProfilePhotoUrl());
                            return m;
                        })
                        .collect(Collectors.toList())
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Message> update(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        String content = body.get("content") != null ? String.valueOf(body.get("content")) : null;
        return ResponseEntity.ok(messageService.updateMessage(id, userId, content));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("Authorization") String authHeader,
                                       @PathVariable Long id) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        messageService.deleteMessage(id, userId);
        return ResponseEntity.noContent().build();
    }
}
