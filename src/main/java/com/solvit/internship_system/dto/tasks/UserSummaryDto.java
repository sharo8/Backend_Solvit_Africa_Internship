package com.solvit.internship_system.dto.tasks;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSummaryDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String universityId;
    private String profilePhotoUrl;
    private String role;
}

