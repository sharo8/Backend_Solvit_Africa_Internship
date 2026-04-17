package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.user.*;
import com.solvit.internship_system.entity.HrApprovalStatus;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.security.CurrentUserResolver;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.service.InternProfileService;
import com.solvit.internship_system.service.InternshipManagementService;
import com.solvit.internship_system.service.UserManagementService;
import com.solvit.internship_system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserManagementService userManagementService;
    private final InternshipManagementService internshipManagementService;
    private final InternProfileService internProfileService;
    private final JwtUtil jwtUtil;
    private final CurrentUserResolver currentUser;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /** List all users (ADMIN, HR). Returns paginated list. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Page<UserResponseDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userManagementService.findAllUsers(page, size));
    }

    /** Search and filter users (ADMIN, HR). */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Page<UserResponseDTO>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) HrApprovalStatus hrApproval,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        return ResponseEntity.ok(userManagementService.searchUsers(q, role, active, hrApproval, page, size, currentUserId));
    }

    /** Create user (ADMIN any role; HR may create INTERN accounts only). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody CreateUserRequestDTO dto,
                                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        Role actorRole = currentUser.requireRole();
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(dto, currentUserId, actorRole));
    }

    /** Update user (ADMIN full; HR may update basic fields for interns only). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateUserRequestDTO dto,
                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        Role actorRole = currentUser.requireRole();
        return ResponseEntity.ok(userManagementService.updateUser(id, dto, currentUserId, actorRole));
    }

    /** Delete user (ADMIN only). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        userManagementService.deleteUser(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> activate(@PathVariable Long id,
                                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        return ResponseEntity.ok(userManagementService.activateUser(id, currentUserId));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> deactivate(@PathVariable Long id,
                                                     @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        return ResponseEntity.ok(userManagementService.deactivateUser(id, currentUserId));
    }

    @PatchMapping("/{id}/approve-intern")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<UserResponseDTO> approveIntern(@PathVariable Long id,
                                                         @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        return ResponseEntity.ok(userManagementService.approveInternRegistration(id, currentUserId));
    }

    @PatchMapping("/{id}/reject-intern")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<UserResponseDTO> rejectIntern(@PathVariable Long id,
                                                        @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        return ResponseEntity.ok(userManagementService.rejectInternRegistration(id, currentUserId));
    }

    @PatchMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResetPasswordResponseDTO> resetPassword(@PathVariable Long id,
                                                                  @RequestBody(required = false) ResetPasswordRequestDTO body,
                                                                  @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        String newPassword = body != null ? body.getNewPassword() : null;
        return ResponseEntity.ok(userManagementService.resetPassword(id, newPassword, currentUserId));
    }

    @PostMapping("/bulk-deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkOperationResponseDTO> bulkDeactivate(@Valid @RequestBody BulkIdsRequestDTO body,
                                                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        return ResponseEntity.ok(userManagementService.bulkDeactivate(body.getIds(), currentUserId));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkOperationResponseDTO> bulkDelete(@Valid @RequestBody BulkIdsRequestDTO body,
                                                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long currentUserId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        return ResponseEntity.ok(userManagementService.bulkDelete(body.getIds(), currentUserId));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<UserStatsDTO> stats() {
        return ResponseEntity.ok(userManagementService.getStats());
    }

    @GetMapping("/interns")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<Page<InternResponseDTO>> getInterns(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUser.requireUserId();
        Role role = currentUser.requireRole();
        Long effectiveSupervisorId = supervisorId;
        if (role == Role.SUPERVISOR) {
            effectiveSupervisorId = userId;
        }
        return ResponseEntity.ok(userManagementService.getInterns(q, active, effectiveSupervisorId, page, size));
    }

    /** Interns whose internship period is not finished (for attendance roster management). */
    @GetMapping("/interns/open-contract")
    @PreAuthorize("hasAnyRole('ADMIN','HR','SUPERVISOR')")
    public ResponseEntity<List<InternResponseDTO>> getInternsOpenContract(
            @RequestParam(required = false) Long supervisorId) {
        Long userId = currentUser.requireUserId();
        Role r = currentUser.requireRole();
        Long effectiveSupervisorId = supervisorId;
        if (r == Role.SUPERVISOR) {
            effectiveSupervisorId = userId;
        }
        return ResponseEntity.ok(userManagementService.getInternsWithOpenContract(effectiveSupervisorId));
    }

    @GetMapping("/interns/{id}/details")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<InternDetailDTO> getInternDetails(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.getInternDetails(id));
    }

    @PatchMapping("/interns/{id}/internship/extend")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<InternshipDatesResponseDto> extendInternship(
            @PathVariable Long id,
            @Valid @RequestBody ExtendInternshipRequestDto dto) {
        Long actorId = currentUser.requireUserId();
        InternProfile p = internshipManagementService.extendInternship(id, dto.getNewEndDate(), actorId);
        return ResponseEntity.ok(userManagementService.toInternshipDatesResponse(p));
    }

    @PostMapping("/interns/{id}/internship/complete")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<InternshipDatesResponseDto> completeInternship(
            @PathVariable Long id,
            @RequestBody(required = false) CompleteInternshipRequestDto dto) {
        Long actorId = currentUser.requireUserId();
        InternProfile p = internshipManagementService.completeInternshipEarly(
                id, dto != null ? dto.getEndDate() : null, actorId);
        return ResponseEntity.ok(userManagementService.toInternshipDatesResponse(p));
    }

    @PutMapping("/interns/{id}/intern-profile")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<InternshipDatesResponseDto> updateInternProfileAsAdmin(
            @PathVariable Long id,
            @RequestBody InternProfile body) {
        InternProfile saved = internProfileService.createOrUpdate(id, body);
        return ResponseEntity.ok(userManagementService.toInternshipDatesResponse(saved));
    }

    @GetMapping("/supervisors")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Page<SupervisorResponseDTO>> getSupervisors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userManagementService.getSupervisors(q, active, page, size));
    }

    @GetMapping("/supervisors/{id}/interns")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<java.util.List<InternResponseDTO>> getSupervisorInterns(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.getSupervisorInterns(id));
    }

    @PostMapping("/supervisors/{id}/assign-intern")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Map<String, String>> assignIntern(@PathVariable Long id,
                                                           @Valid @RequestBody AssignInternRequestDTO body) {
        Long actorId = currentUser.requireUserId();
        userManagementService.assignInternToSupervisor(id, body.getInternId(), actorId);
        return ResponseEntity.ok(Map.of("message", "Intern assigned"));
    }

    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> updateMe(@RequestBody Map<String, Object> body) {
        Long userId = currentUser.requireUserId();
        String firstName = (String) body.get("firstName");
        String lastName = (String) body.get("lastName");
        String universityId = (String) body.get("universityId");
        Boolean profileCompleted = body.get("profileCompleted") != null ? (Boolean) body.get("profileCompleted") : null;
        userService.updateProfile(userId, firstName, lastName, universityId,
                profileCompleted != null && profileCompleted);
        return ResponseEntity.ok(userManagementService.getById(userId));
    }

    /** Set profile photo URL (e.g. after uploading via POST /api/users/me/profile-photo/upload or /api/files/upload). */
    @PatchMapping("/me/profile-photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> setProfilePhoto(@RequestBody Map<String, String> body) {
        Long userId = currentUser.requireUserId();
        String url = body.get("profilePhotoUrl");
        if (url == null || url.isBlank()) return ResponseEntity.badRequest().build();
        userService.updateProfilePhoto(userId, url);
        return ResponseEntity.ok(userManagementService.getById(userId));
    }

    /** Upload profile photo (multipart). Saves file and sets user's profilePhotoUrl. */
    @PostMapping("/me/profile-photo/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadProfilePhoto(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        Long userId = currentUser.requireUserId();
        try {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.')) : ".jpg";
            String filename = "profile-" + userId + "-" + UUID.randomUUID().toString() + ext;
            Path dir = Paths.get(uploadDir);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path path = dir.resolve(filename);
            Files.copy(file.getInputStream(), path);
            String url = "/api/files/download/" + filename;
            userService.updateProfilePhoto(userId, url);
            return ResponseEntity.ok(Map.of("url", url, "filename", filename, "user", userManagementService.getById(userId)));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }

    /**
     * Numeric id only — prevents {@code /me} from being captured as {@code id="me"} (Long conversion → 500).
     */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.getById(id));
    }
}
