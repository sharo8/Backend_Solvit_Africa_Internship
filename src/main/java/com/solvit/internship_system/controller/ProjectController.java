package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.tasks.CreateProjectRequestDto;
import com.solvit.internship_system.dto.tasks.ProjectDto;
import com.solvit.internship_system.dto.tasks.UpdateProjectRequestDto;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.ProjectService;
import com.solvit.internship_system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR')")
    public ResponseEntity<List<ProjectDto>> list(@RequestHeader("Authorization") String authHeader) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        User requester = userService.getById(uid);
        if (requester.getRole() == Role.SUPERVISOR) {
            return ResponseEntity.ok(projectService.listActiveForSupervisor(uid));
        }
        return ResponseEntity.ok(projectService.listActive());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ProjectDto> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateProjectRequestDto dto
    ) {
        Long editorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectService.create(dto, editorId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ProjectDto> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody UpdateProjectRequestDto dto
    ) {
        Long editorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectService.update(id, dto, editorId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<Map<String, String>> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id
    ) {
        Long editorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        projectService.softDelete(id, editorId);
        return ResponseEntity.ok(Map.of("message", "Project deleted"));
    }
}

