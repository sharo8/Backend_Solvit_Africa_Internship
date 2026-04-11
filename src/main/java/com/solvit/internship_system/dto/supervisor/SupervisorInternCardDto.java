package com.solvit.internship_system.dto.supervisor;

import lombok.Builder;

@Builder
public record SupervisorInternCardDto(
        Long internId,
        String firstName,
        String lastName,
        String initials,
        String avatarUrl,
        String email,
        String universityId,
        String institution,
        String companyName,
        Integer profileCompletenessPercent,
        boolean active
) {
}
