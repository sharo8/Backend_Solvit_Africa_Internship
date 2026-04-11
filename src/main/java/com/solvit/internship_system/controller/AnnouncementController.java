package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.AnnouncementDto;
import com.solvit.internship_system.dto.AnnouncementUpsertRequest;
import com.solvit.internship_system.entity.Announcement;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.AnnouncementService;
import com.solvit.internship_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AnnouncementDto> create(@RequestHeader("Authorization") String authHeader,
                                                  @RequestBody AnnouncementUpsertRequest announcement) {
        Long authorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(toDto(announcementService.create(authorId, announcement)));
    }

    @GetMapping
    public ResponseEntity<Page<AnnouncementDto>> getVisibleForCurrentUser(
            Pageable pageable,
            @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        User user = userService.getById(userId);
        return ResponseEntity.ok(announcementService.getByRole(user.getRole(), pageable).map(this::toDto));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AnnouncementDto>> getAll(Pageable pageable) {
        return ResponseEntity.ok(announcementService.getAll(pageable).map(this::toDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnouncementDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toDto(announcementService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AnnouncementDto> update(@PathVariable Long id,
                                                  @RequestBody AnnouncementUpsertRequest request) {
        return ResponseEntity.ok(toDto(announcementService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/visible/count")
    public ResponseEntity<java.util.Map<String, Long>> visibleCount(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        User user = userService.getById(userId);
        return ResponseEntity.ok(java.util.Map.of("count", announcementService.countVisibleForRole(user.getRole())));
    }

    private AnnouncementDto toDto(Announcement a) {
        String name = a.getAuthor() != null ? (a.getAuthor().getFirstName() + " " + a.getAuthor().getLastName()) : null;
        return AnnouncementDto.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .targetRole(a.getTargetRole())
                .pinned(a.isPinned())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .authorId(a.getAuthor() != null ? a.getAuthor().getId() : null)
                .authorName(name)
                .build();
    }
}
