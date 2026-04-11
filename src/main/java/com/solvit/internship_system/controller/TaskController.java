package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.tasks.CreateTaskRequestDto;
import com.solvit.internship_system.dto.tasks.PatchTaskStatusRequestDto;
import com.solvit.internship_system.dto.tasks.TaskDto;
import com.solvit.internship_system.dto.tasks.TasksCreatedResponseDto;
import com.solvit.internship_system.dto.tasks.UpdateTaskProgressRequestDto;
import com.solvit.internship_system.dto.tasks.UpdateTaskRequestDto;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.security.CurrentUserResolver;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final JwtUtil jwtUtil;
    private final CurrentUserResolver currentUser;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<TasksCreatedResponseDto> create(@RequestHeader("Authorization") String authHeader,
                                                          @Valid @RequestBody CreateTaskRequestDto dto) {
        Long assignerId = currentUser.requireUserId();
        return ResponseEntity.ok(taskService.create(dto, assignerId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR')")
    public ResponseEntity<Page<TaskDto>> listFiltered(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(required = false) Long projectId,
            Pageable pageable
    ) {
        Long userId = currentUser.requireUserId();
        Role role = currentUser.requireRole();
        log.debug(
                "[TaskController.list] viewerUserId={} role={} params: status={} assignedTo={} projectId={} page={} size={}",
                userId,
                role,
                status,
                assignedTo,
                projectId,
                pageable.getPageNumber(),
                pageable.getPageSize());
        logJwtVersusSecurityRole(authHeader, role);
        Page<TaskDto> page = taskService.listFiltered(status, assignedTo, projectId, pageable, userId, role);
        Long scope = role == Role.SUPERVISOR ? userId : null;
        log.info(
                "[tasks] GET /api/tasks userId={} role={} supervisorScopeId={} filters=[status={},assignedTo={},projectId={}] page={} size={} -> totalElements={} numberOfElements={}",
                userId,
                role,
                scope,
                status,
                assignedTo,
                projectId,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                page.getTotalElements(),
                page.getNumberOfElements());
        if (log.isDebugEnabled() && !page.getContent().isEmpty()) {
            log.debug(
                    "[tasks] first task ids: {}",
                    page.getContent().stream().map(TaskDto::getId).limit(5).toList());
        }
        return ResponseEntity.ok(page);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('INTERN','ADMIN','SUPERVISOR')")
    public ResponseEntity<TaskDto> patchStatus(@RequestHeader("Authorization") String authHeader,
                                               @PathVariable Long id,
                                               @Valid @RequestBody PatchTaskStatusRequestDto body) {
        Long userId = currentUser.requireUserId();
        return ResponseEntity.ok(taskService.patchStatus(id, body, userId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<Page<TaskDto>> getMyTasks(@RequestHeader("Authorization") String authHeader,
                                                     Pageable pageable) {
        Long userId = currentUser.requireUserId();
        return ResponseEntity.ok(taskService.getByAssignee(userId, pageable));
    }

    /** Admin-only: raw row counts in the connected database (troubleshooting empty task list). */
    @GetMapping("/diagnostics/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> taskDiagnosticsSummary() {
        return ResponseEntity.ok(taskService.adminTaskTableDiagnostics());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskDto> getById(@RequestHeader("Authorization") String authHeader,
                                           @PathVariable Long id) {
        Long userId = currentUser.requireUserId();
        Role role = currentUser.requireRole();
        return ResponseEntity.ok(taskService.getByIdForActor(id, userId, role));
    }

    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<TaskDto> updateProgress(@RequestHeader("Authorization") String authHeader,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody UpdateTaskProgressRequestDto body) {
        Long userId = currentUser.requireUserId();
        return ResponseEntity.ok(taskService.updateProgress(id, body, userId));
    }

    /** Accumulate timer seconds for the assignee (intern). */
    @PatchMapping("/{id}/time-log")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<TaskDto> logTime(@RequestHeader("Authorization") String authHeader,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, Integer> body) {
        Long userId = currentUser.requireUserId();
        Integer add = body != null ? body.get("additionalSeconds") : null;
        if (add == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(taskService.logTime(id, add, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<TaskDto> update(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable Long id,
                                          @Valid @RequestBody UpdateTaskRequestDto dto) {
        Long editorId = currentUser.requireUserId();
        return ResponseEntity.ok(taskService.update(id, dto, editorId));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<TaskDto> validate(@RequestHeader("Authorization") String authHeader,
                                              @PathVariable Long id) {
        Long supervisorId = currentUser.requireUserId();
        return ResponseEntity.ok(taskService.validate(id, supervisorId));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<TaskDto> reject(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, String> body) {
        Long supervisorId = currentUser.requireUserId();
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(taskService.reject(id, reason, supervisorId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(@RequestHeader("Authorization") String authHeader,
                                                       @PathVariable Long id) {
        Long userId = currentUser.requireUserId();
        taskService.softDelete(id, userId);
        return ResponseEntity.ok(Map.of("message", "Task deleted"));
    }

    /** HR: global counts (read-only oversight). INTERN: own tasks. SUPERVISOR: scoped. ADMIN: unfiltered. */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR','INTERN')")
    public ResponseEntity<Map<String, Long>> stats(@RequestHeader("Authorization") String authHeader) {
        Long userId = currentUser.requireUserId();
        Role role = currentUser.requireRole();
        logJwtVersusSecurityRole(authHeader, role);
        Map<String, Long> out = taskService.stats(userId, role);
        Long scope = role == Role.SUPERVISOR ? userId : null;
        log.info("[tasks] GET /api/tasks/stats userId={} role={} supervisorScopeId={} -> {}", userId, role, scope, out);
        return ResponseEntity.ok(out);
    }

    /** If JWT role claim disagrees with Spring Security (DB) role, list/stats used to be wrong — log for support. */
    private void logJwtVersusSecurityRole(String authHeader, Role securityRole) {
        try {
            String token = authHeader.substring(7);
            String jwtRole = jwtUtil.getRoleFromToken(token);
            if (jwtRole != null && !jwtRole.equalsIgnoreCase(securityRole.name())) {
                log.warn(
                        "[tasks] JWT role claim ({}) differs from SecurityContext role ({}) — using SecurityContext for scoping. Re-login to refresh the token if the UI shows another role.",
                        jwtRole,
                        securityRole);
            }
        } catch (Exception e) {
            log.debug("[tasks] Could not read JWT role for comparison: {}", e.getMessage());
        }
    }

}
