package com.solvit.internship_system.dto;

import com.solvit.internship_system.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean profileCompleted;
    private boolean emailVerified;
    private boolean requiresFirstLoginSetup;
    /** True after public intern registration until HR/Admin approves (no tokens issued). */
    private boolean pendingApproval;
}
