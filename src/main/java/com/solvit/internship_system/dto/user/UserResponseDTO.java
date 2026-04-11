package com.solvit.internship_system.dto.user;

import com.solvit.internship_system.entity.HrApprovalStatus;
import com.solvit.internship_system.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserResponseDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private HrApprovalStatus hrApprovalStatus;
    private String universityId;
    private boolean active;
    private boolean emailVerified;
    private boolean profileCompleted;
    private String profilePhotoUrl;
    private Instant createdAt;
    private Instant lastLoginAt;
}
