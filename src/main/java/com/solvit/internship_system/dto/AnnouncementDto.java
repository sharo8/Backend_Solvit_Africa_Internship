package com.solvit.internship_system.dto;

import com.solvit.internship_system.entity.Role;
import lombok.Builder;

import java.time.Instant;

@Builder
public record AnnouncementDto(
        Long id,
        String title,
        String content,
        Role targetRole,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt,
        Long authorId,
        String authorName
) {}
