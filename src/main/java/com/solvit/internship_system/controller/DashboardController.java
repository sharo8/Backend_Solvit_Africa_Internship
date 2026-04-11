package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.DashboardService;
import com.solvit.internship_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKpis(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        User user = userService.getById(userId);
        return ResponseEntity.ok(dashboardService.getKpis(userId, user.getRole()));
    }
}
