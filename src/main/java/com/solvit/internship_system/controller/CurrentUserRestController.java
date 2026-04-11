package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.user.UserResponseDTO;
import com.solvit.internship_system.security.CurrentUserResolver;
import com.solvit.internship_system.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Isolated controller for {@code GET /api/users/me} so it never competes with {@code /api/users/{id}} routing.
 */
@RestController
@RequiredArgsConstructor
public class CurrentUserRestController {

    private final CurrentUserResolver currentUser;
    private final UserManagementService userManagementService;

    @GetMapping("/api/users/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser() {
        return ResponseEntity.ok(userManagementService.getById(currentUser.requireUserId()));
    }
}
