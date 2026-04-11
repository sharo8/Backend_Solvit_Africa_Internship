package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.supervisor.SupervisorInternCardDto;
import com.solvit.internship_system.dto.tasks.ProjectDto;
import com.solvit.internship_system.security.CurrentUserPrincipal;
import com.solvit.internship_system.service.ProjectService;
import com.solvit.internship_system.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supervisors/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISOR')")
public class SupervisorScopeController {

    private final UserManagementService userManagementService;
    private final ProjectService projectService;

    @GetMapping("/interns")
    public ResponseEntity<List<SupervisorInternCardDto>> myInterns(
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        return ResponseEntity.ok(userManagementService.getInternCardsForSupervisor(principal.getUserId()));
    }

    @GetMapping("/interns/{internUserId}")
    public ResponseEntity<SupervisorInternCardDto> myInternById(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable Long internUserId
    ) {
        return ResponseEntity.ok(
                userManagementService.getInternCardForSupervisor(principal.getUserId(), internUserId));
    }

    @GetMapping("/projects/active")
    public ResponseEntity<List<ProjectDto>> myActiveProjects(
            @AuthenticationPrincipal CurrentUserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.listActiveForSupervisor(principal.getUserId()));
    }
}
