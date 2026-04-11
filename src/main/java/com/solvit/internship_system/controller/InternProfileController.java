package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.InternProfileService;
import com.solvit.internship_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/intern-profiles")
@RequiredArgsConstructor
public class InternProfileController {

    private final InternProfileService profileService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<InternProfile> getMyProfile(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(profileService.getByUserIdOrCreate(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<InternProfile> updateMyProfile(@RequestHeader("Authorization") String authHeader,
                                                       @RequestBody InternProfile body) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(profileService.createOrUpdate(userId, body));
    }

    @GetMapping("/supervisor/{supervisorId}")
    public ResponseEntity<List<InternProfile>> getBySupervisor(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long supervisorId) {
        Long actorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        User actor = userService.getById(actorId);
        if (actor.getRole() == Role.SUPERVISOR && !actorId.equals(supervisorId)) {
            throw new AccessDeniedException("You can only access your own interns");
        }
        return ResponseEntity.ok(profileService.getBySupervisor(supervisorId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternProfile> getById(@PathVariable Long id) {
        return ResponseEntity.ok(profileService.getById(id));
    }
}
