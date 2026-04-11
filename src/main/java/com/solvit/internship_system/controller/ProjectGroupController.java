package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.groups.CreateProjectGroupRequestDto;
import com.solvit.internship_system.dto.groups.ProjectGroupDto;
import com.solvit.internship_system.dto.groups.UpdateProjectGroupRequestDto;
import com.solvit.internship_system.dto.tasks.UserSummaryDto;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.ProjectGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class ProjectGroupController {

    private final ProjectGroupService projectGroupService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectGroupDto> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateProjectGroupRequestDto dto) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.create(dto, uid));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<Page<ProjectGroupDto>> list(
            @RequestHeader("Authorization") String authHeader,
            Pageable pageable) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.list(pageable, uid));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<ProjectGroupDto> getById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.getById(id, uid));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectGroupDto> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody UpdateProjectGroupRequestDto dto) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.update(id, dto, uid));
    }

    @PostMapping("/{id}/interns/{internId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<ProjectGroupDto> addIntern(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @PathVariable Long internId) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.addIntern(id, internId, uid));
    }

    @DeleteMapping("/{id}/interns/{internId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<ProjectGroupDto> removeIntern(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @PathVariable Long internId) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.removeIntern(id, internId, uid));
    }

    @PutMapping("/{id}/supervisor/{supervisorUserId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ProjectGroupDto> assignSupervisor(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @PathVariable Long supervisorUserId) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.assignSupervisor(id, supervisorUserId, uid));
    }

    @GetMapping("/{id}/interns")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<List<UserSummaryDto>> listInterns(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(projectGroupService.listInterns(id, uid));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long uid = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        projectGroupService.softDelete(id, uid);
        return ResponseEntity.ok(Map.of("message", "Project group deleted"));
    }
}
