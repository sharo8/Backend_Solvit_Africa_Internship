package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.user.*;
import com.solvit.internship_system.dto.supervisor.SupervisorInternCardDto;
import org.springframework.security.access.AccessDeniedException;
import com.solvit.internship_system.entity.*;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ConflictException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.*;
import com.solvit.internship_system.validation.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final String ENTITY_USER = "USER";

    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final AttendanceRepository attendanceRepository;
    private final TaskRepository taskRepository;
    private final PerformanceScoreRepository performanceScoreRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendBaseUrl;

    public Page<UserResponseDTO> searchUsers(String q, Role role, Boolean active, HrApprovalStatus hrApproval, int page, int size, Long performedByUserId) {
        Page<User> users = userRepository.searchUsers(q, role, active, hrApproval, PageRequest.of(page, size));
        return users.map(this::toUserResponseDTO);
    }

    public Page<UserResponseDTO> findAllUsers(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size)).map(this::toUserResponseDTO);
    }

    public UserResponseDTO getById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        return toUserResponseDTO(user);
    }

    @Transactional
    public UserResponseDTO createUser(CreateUserRequestDTO dto, Long performedByUserId, Role actorRole) {
        if (actorRole == Role.HR && dto.getRole() != Role.INTERN) {
            throw new AccessDeniedException("HR may only create intern accounts");
        }
        PasswordPolicy.validate(dto.getPassword());
        if (userRepository.existsByEmail(dto.getEmail().trim().toLowerCase())) {
            throw new ConflictException("Email already in use");
        }
        User user = User.builder()
                .firstName(dto.getFirstName().trim())
                .lastName(dto.getLastName().trim())
                .email(dto.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(dto.getRole())
                .universityId(dto.getUniversityId() != null ? dto.getUniversityId().trim() : null)
                .profilePhotoUrl(dto.getProfilePhotoUrl() != null && !dto.getProfilePhotoUrl().isBlank() ? dto.getProfilePhotoUrl().trim() : null)
                .active(true)
                .emailVerified(false)
                .mfaEnabled(false)
                .profileCompleted(false)
                .consentGiven(false)
                .hrApprovalStatus(HrApprovalStatus.APPROVED)
                .createdById(performedByUserId)
                .firstLogin(true)
                .build();
        user = userRepository.save(user);
        String setPasswordLink = null;
        if (performedByUserId != null) {
            passwordResetTokenRepository.deleteByUser_Id(user.getId());
            String token = UUID.randomUUID().toString();
            com.solvit.internship_system.entity.PasswordResetToken prt = com.solvit.internship_system.entity.PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiresAt(Instant.now().plusSeconds(TimeUnit.HOURS.toSeconds(48)))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(prt);
            setPasswordLink = frontendBaseUrl + "/reset-password?token=" + token;
        }
        emailService.sendWelcomeEmail(user.getFirstName(), user.getEmail(), setPasswordLink);
        auditService.log(performedByUserId, "CREATE", ENTITY_USER, user.getId(), null, null, null, null);
        return toUserResponseDTO(user);
    }

    @Transactional
    public UserResponseDTO updateUser(Long id, UpdateUserRequestDTO dto, Long performedByUserId, Role actorRole) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (actorRole == Role.HR) {
            if (user.getRole() != Role.INTERN) {
                throw new AccessDeniedException("HR may only edit intern profiles");
            }
            if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName().trim());
            if (dto.getLastName() != null) user.setLastName(dto.getLastName().trim());
            if (dto.getEmail() != null) {
                String email = dto.getEmail().trim().toLowerCase();
                if (!email.equals(user.getEmail()) && userRepository.existsByEmailAndIdNot(email, id)) {
                    throw new ConflictException("Email already in use");
                }
                user.setEmail(email);
            }
            if (dto.getUniversityId() != null) {
                user.setUniversityId(dto.getUniversityId().trim().isEmpty() ? null : dto.getUniversityId().trim());
            }
            if (dto.getProfilePhotoUrl() != null) {
                user.setProfilePhotoUrl(dto.getProfilePhotoUrl().trim().isEmpty() ? null : dto.getProfilePhotoUrl().trim());
            }
            user = userRepository.save(user);
            auditService.log(performedByUserId, "UPDATE", ENTITY_USER, user.getId(), null, "hr_intern_profile", null, null);
            return toUserResponseDTO(user);
        }

        Role roleBeforeUpdate = user.getRole();
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName().trim());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName().trim());
        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim().toLowerCase();
            if (!email.equals(user.getEmail()) && userRepository.existsByEmailAndIdNot(email, id)) {
                throw new ConflictException("Email already in use");
            }
            user.setEmail(email);
        }
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getUniversityId() != null) user.setUniversityId(dto.getUniversityId().trim().isEmpty() ? null : dto.getUniversityId().trim());
        if (dto.getActive() != null) {
            if (Boolean.FALSE.equals(dto.getActive())) {
                user.setAuthSessionId(null);
            }
            user.setActive(dto.getActive());
        }
        if (dto.getProfilePhotoUrl() != null) user.setProfilePhotoUrl(dto.getProfilePhotoUrl().trim().isEmpty() ? null : dto.getProfilePhotoUrl().trim());
        user = userRepository.save(user);
        if (dto.getRole() != null && dto.getRole() != roleBeforeUpdate) {
            auditService.log(
                    performedByUserId,
                    "UPDATE",
                    ENTITY_USER,
                    user.getId(),
                    null,
                    "role_changed:" + roleBeforeUpdate + "->" + dto.getRole() + ";client_should_relogin",
                    null,
                    null);
        } else {
            auditService.log(performedByUserId, "UPDATE", ENTITY_USER, user.getId(), null, null, null, null);
        }
        return toUserResponseDTO(user);
    }

    @Transactional
    public void deleteUser(Long id, Long performedByUserId) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.getId().equals(performedByUserId)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new BadRequestException("Cannot delete the last administrator");
            }
        }
        auditService.log(performedByUserId, "DELETE", ENTITY_USER, user.getId(), null, null, null, null);
        userRepository.delete(user);
    }

    @Transactional
    public UserResponseDTO activateUser(Long id, Long performedByUserId) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setActive(true);
        if (user.getHrApprovalStatus() == HrApprovalStatus.PENDING) {
            user.setHrApprovalStatus(HrApprovalStatus.APPROVED);
        }
        user = userRepository.save(user);
        auditService.log(performedByUserId, "UPDATE", ENTITY_USER, user.getId(), null, "active=true", null, null);
        return toUserResponseDTO(user);
    }

    @Transactional
    public UserResponseDTO deactivateUser(Long id, Long performedByUserId) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        user.setActive(false);
        user.setAuthSessionId(null);
        user = userRepository.save(user);
        auditService.log(performedByUserId, "UPDATE", ENTITY_USER, user.getId(), null, "active=false", null, null);
        return toUserResponseDTO(user);
    }

    @Transactional
    public ResetPasswordResponseDTO resetPassword(Long id, String newPassword, Long performedByUserId) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        String temporaryPassword;
        if (newPassword != null && !newPassword.isBlank()) {
            PasswordPolicy.validate(newPassword);
            temporaryPassword = newPassword;
        } else {
            temporaryPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setAuthSessionId(null);
        userRepository.save(user);
        auditService.log(performedByUserId, "UPDATE", ENTITY_USER, user.getId(), null, "password_reset", null, null);
        return ResetPasswordResponseDTO.builder()
                .message("Password reset")
                .temporaryPassword(temporaryPassword)
                .build();
    }

    @Transactional
    public BulkOperationResponseDTO bulkDeactivate(List<Long> ids, Long performedByUserId) {
        int updated = 0;
        for (Long id : ids) {
            Optional<User> opt = userRepository.findById(id);
            if (opt.isPresent()) {
                User user = opt.get();
                user.setActive(false);
                user.setAuthSessionId(null);
                userRepository.save(user);
                updated++;
            }
        }
        auditService.log(performedByUserId, "UPDATE", ENTITY_USER, null, null, "bulk_deactivate", null, null);
        return BulkOperationResponseDTO.builder()
                .updated(updated)
                .message(updated + " user(s) deactivated")
                .build();
    }

    @Transactional
    public BulkOperationResponseDTO bulkDelete(List<Long> ids, Long performedByUserId) {
        Set<Long> adminIds = userRepository.findAllById(ids).stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .map(User::getId)
                .collect(Collectors.toSet());
        long totalAdmins = userRepository.countByRole(Role.ADMIN);
        int deleted = 0;
        int skipped = 0;
        for (Long id : ids) {
            if (adminIds.contains(id) && totalAdmins <= 1) {
                skipped++;
                continue;
            }
            if (id.equals(performedByUserId)) {
                skipped++;
                continue;
            }
            Optional<User> opt = userRepository.findById(id);
            if (opt.isEmpty()) continue;
            User u = opt.get();
            if (u.getRole() == Role.ADMIN) {
                skipped++;
                continue;
            }
            auditService.log(performedByUserId, "DELETE", ENTITY_USER, u.getId(), null, null, null, null);
            userRepository.delete(u);
            deleted++;
        }
        return BulkOperationResponseDTO.builder()
                .deleted(deleted)
                .skipped(skipped)
                .message("Deleted " + deleted + " user(s), skipped " + skipped)
                .build();
    }

    public UserStatsDTO getStats() {
        long total = userRepository.count();
        long active = userRepository.countByActive(true);
        long inactive = userRepository.countByActive(false);
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        long newThisMonth = userRepository.countByCreatedAtAfter(startOfMonth);
        long verifiedEmails = userRepository.countByEmailVerified(true);
        Map<String, Long> byRole = new HashMap<>();
        for (Role r : Role.values()) {
            byRole.put(r.name(), userRepository.countByRole(r));
        }
        return UserStatsDTO.builder()
                .total(total)
                .active(active)
                .inactive(inactive)
                .byRole(byRole)
                .newThisMonth(newThisMonth)
                .verifiedEmails(verifiedEmails)
                .build();
    }

    public Page<InternResponseDTO> getInterns(String q, Boolean active, Long supervisorId, int page, int size) {
        Page<User> users;
        if (supervisorId != null) {
            List<Long> internIds = internProfileRepository.findBySupervisorUserId(supervisorId).stream()
                    .map(ip -> ip.getUser().getId())
                    .toList();
            if (internIds.isEmpty()) {
                return Page.empty(PageRequest.of(page, size));
            }
            users = userRepository.findInternsByIdInAndSearch(internIds, q, active, PageRequest.of(page, size));
        } else {
            users = userRepository.searchUsers(q, Role.INTERN, active, null, PageRequest.of(page, size));
        }
        return users.map(this::toInternResponseDTO);
    }

    /** Active interns: contract end date ≥ today (Kigali), both dates set on profile. */
    public List<InternResponseDTO> getInternsWithOpenContract(Long supervisorId) {
        LocalDate today = AttendanceCalculationService.todayKigali();
        List<InternProfile> profiles = internProfileRepository.findWithOpenContractOnOrAfter(today);
        return profiles.stream()
                .filter(p -> supervisorId == null || Objects.equals(p.getSupervisorUserId(), supervisorId))
                .map(p -> toInternResponseDTO(p.getUser()))
                .sorted(Comparator.comparing(InternResponseDTO::getLastName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public InternDetailDTO getInternDetails(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        UserResponseDTO userDto = toUserResponseDTO(user);
        LocalDate now = AttendanceCalculationService.todayKigali();
        YearMonth ym = YearMonth.from(now);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        Optional<InternProfile> ipOpt = internProfileRepository.findByUser_Id(id);
        long expectedWorkdays = ipOpt.map(ip -> InternshipAttendanceRules.countExpectedWorkdaysInMonth(ip, ym)).orElse(0L);
        List<Attendance> attendances = attendanceRepository.findForUserInDateRange(id, monthStart, monthEnd);
        long countedDays = attendances.stream()
                .filter(a -> InternshipAttendanceRules.isWorkday(a.getAttendanceDate()))
                .filter(a -> ipOpt.isPresent() && InternshipAttendanceRules.isWithinContract(ipOpt.get(), a.getAttendanceDate()))
                .filter(a -> a.getStatus() != Attendance.AttendanceStatus.ABSENT && a.getStatus() != Attendance.AttendanceStatus.PENDING)
                .count();
        double attendanceRate = expectedWorkdays > 0 ? (countedDays * 100.0 / expectedWorkdays) : 0;
        long tasksCompleted = taskRepository.countByActiveTrueAndAssignee_IdAndStatusIn(id,
                Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.VALIDATED));
        long tasksPending = taskRepository.countByActiveTrueAndAssignee_IdAndStatus(id, Task.TaskStatus.PENDING)
                + taskRepository.countByActiveTrueAndAssignee_IdAndStatus(id, Task.TaskStatus.IN_PROGRESS)
                + taskRepository.countByActiveTrueAndAssignee_IdAndStatus(id, Task.TaskStatus.OVERDUE);
        Double performanceScore = performanceScoreRepository.findByIntern_IdOrderByCreatedAtDesc(id, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(PerformanceScore::getOverallScore)
                .orElse(null);
        InternDetailDTO.InternDetailDTOBuilder b = InternDetailDTO.builder()
                .user(userDto)
                .attendanceRate(attendanceRate)
                .tasksCompleted(tasksCompleted)
                .tasksPending(tasksPending)
                .performanceScore(performanceScore)
                .lastLoginAt(user.getLastLoginAt());
        if (ipOpt.isPresent()) {
            InternProfile ip = ipOpt.get();
            b.internshipStartDate(ip.getInternshipStartDate())
                    .internshipEndDate(ip.getInternshipEndDate())
                    .internshipStatus(InternshipAttendanceRules.computeInternshipStatus(ip, now));
        } else {
            b.internshipStatus("NO_DATES");
        }
        return b.build();
    }

    public InternshipDatesResponseDto toInternshipDatesResponse(InternProfile ip) {
        LocalDate today = AttendanceCalculationService.todayKigali();
        return InternshipDatesResponseDto.builder()
                .internshipStartDate(ip.getInternshipStartDate())
                .internshipEndDate(ip.getInternshipEndDate())
                .internshipStatus(InternshipAttendanceRules.computeInternshipStatus(ip, today))
                .build();
    }

    public Page<SupervisorResponseDTO> getSupervisors(String q, Boolean active, int page, int size) {
        Page<User> users = userRepository.searchUsers(q, Role.SUPERVISOR, active, null, PageRequest.of(page, size));
        return users.map(this::toSupervisorResponseDTO);
    }

    public List<InternResponseDTO> getSupervisorInterns(Long supervisorId) {
        List<InternProfile> profiles = internProfileRepository.findBySupervisorUserId(supervisorId);
        List<Long> ids = profiles.stream().map(ip -> ip.getUser().getId()).toList();
        if (ids.isEmpty()) return List.of();
        List<User> interns = userRepository.findAllById(ids);
        return interns.stream().map(this::toInternResponseDTO).toList();
    }

    public List<SupervisorInternCardDto> getInternCardsForSupervisor(Long supervisorId) {
        return internProfileRepository.findBySupervisorUserId(supervisorId).stream()
                .filter(ip -> ip.getUser() != null)
                .sorted(Comparator.comparing(
                        ip -> (ip.getUser().getFirstName() + " " + ip.getUser().getLastName()).trim(),
                        String.CASE_INSENSITIVE_ORDER))
                .map(ip -> {
                    User u = ip.getUser();
                    String first = u.getFirstName() != null ? u.getFirstName() : "";
                    String last = u.getLastName() != null ? u.getLastName() : "";
                    return SupervisorInternCardDto.builder()
                            .internId(u.getId())
                            .firstName(first)
                            .lastName(last)
                            .initials(initials(first, last))
                            .avatarUrl(u.getProfilePhotoUrl())
                            .email(u.getEmail())
                            .universityId(u.getUniversityId())
                            .institution(ip.getInstitution())
                            .companyName(ip.getCompanyName())
                            .profileCompletenessPercent(ip.getProfileCompletenessPercent() != null ? ip.getProfileCompletenessPercent() : 0)
                            .active(u.isActive())
                            .build();
                })
                .toList();
    }

    public SupervisorInternCardDto getInternCardForSupervisor(Long supervisorId, Long internUserId) {
        InternProfile ip = internProfileRepository.findByUser_Id(internUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", internUserId));
        if (!Objects.equals(ip.getSupervisorUserId(), supervisorId)) {
            throw new AccessDeniedException("This intern is not under your supervision");
        }
        User u = ip.getUser();
        if (u == null || u.getRole() != Role.INTERN) {
            throw new BadRequestException("Invalid intern user");
        }
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String last = u.getLastName() != null ? u.getLastName() : "";
        return SupervisorInternCardDto.builder()
                .internId(u.getId())
                .firstName(first)
                .lastName(last)
                .initials(initials(first, last))
                .avatarUrl(u.getProfilePhotoUrl())
                .email(u.getEmail())
                .universityId(u.getUniversityId())
                .institution(ip.getInstitution())
                .companyName(ip.getCompanyName())
                .profileCompletenessPercent(ip.getProfileCompletenessPercent() != null ? ip.getProfileCompletenessPercent() : 0)
                .active(u.isActive())
                .build();
    }

    @Transactional
    public void assignInternToSupervisor(Long supervisorId, Long internId, Long performedByUserId) {
        User supervisor = userRepository.findById(supervisorId).orElseThrow(() -> new ResourceNotFoundException("User", supervisorId));
        if (supervisor.getRole() != Role.SUPERVISOR) {
            throw new BadRequestException("User is not a supervisor");
        }
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        InternProfile profile = internProfileRepository.findByUser_Id(internId).orElse(null);
        if (profile == null) {
            profile = InternProfile.builder().user(intern).supervisorUserId(supervisorId).build();
        } else {
            profile.setSupervisorUserId(supervisorId);
        }
        internProfileRepository.save(profile);
        auditService.log(performedByUserId, "UPDATE", "InternProfile", profile.getId(), null, "supervisor_assigned", null, null);
    }

    @Transactional
    public UserResponseDTO approveInternRegistration(Long internUserId, Long performedByUserId) {
        User user = userRepository.findById(internUserId).orElseThrow(() -> new ResourceNotFoundException("User", internUserId));
        if (user.getRole() != Role.INTERN) {
            throw new BadRequestException("Only intern accounts can be approved through this action.");
        }
        if (user.getHrApprovalStatus() != HrApprovalStatus.PENDING && user.getHrApprovalStatus() != HrApprovalStatus.REJECTED) {
            throw new BadRequestException("This account is not awaiting approval.");
        }
        user.setHrApprovalStatus(HrApprovalStatus.APPROVED);
        user.setActive(true);
        user = userRepository.save(user);
        emailService.sendNotificationEmail(user.getEmail(), "[SOLVIT Africa] Your intern account was approved",
                String.format("Hello %s,%n%nYour intern account has been approved. You can sign in at: %s/login%n%n— SOLVIT Africa",
                        user.getFirstName() != null ? user.getFirstName() : "there",
                        frontendBaseUrl.replaceAll("/$", "")));
        auditService.log(performedByUserId, "APPROVE_INTERN", ENTITY_USER, user.getId(), null, null, null, null);
        return toUserResponseDTO(user);
    }

    @Transactional
    public UserResponseDTO rejectInternRegistration(Long internUserId, Long performedByUserId) {
        User user = userRepository.findById(internUserId).orElseThrow(() -> new ResourceNotFoundException("User", internUserId));
        if (user.getRole() != Role.INTERN) {
            throw new BadRequestException("Only intern accounts can be rejected through this action.");
        }
        if (user.getHrApprovalStatus() != HrApprovalStatus.PENDING) {
            throw new BadRequestException("Only pending registrations can be rejected.");
        }
        user.setHrApprovalStatus(HrApprovalStatus.REJECTED);
        user.setActive(false);
        user.setAuthSessionId(null);
        user = userRepository.save(user);
        emailService.sendNotificationEmail(user.getEmail(), "[SOLVIT Africa] Intern registration update",
                String.format("Hello %s,%n%nYour intern registration was not approved at this time. Contact your organization for details.%n%n— SOLVIT Africa",
                        user.getFirstName() != null ? user.getFirstName() : "there"));
        auditService.log(performedByUserId, "REJECT_INTERN", ENTITY_USER, user.getId(), null, null, null, null);
        return toUserResponseDTO(user);
    }

    private UserResponseDTO toUserResponseDTO(User u) {
        return UserResponseDTO.builder()
                .id(u.getId())
                .firstName(u.getFirstName() != null ? u.getFirstName() : "")
                .lastName(u.getLastName() != null ? u.getLastName() : "")
                .email(u.getEmail() != null ? u.getEmail() : "")
                .role(u.getRole())
                .hrApprovalStatus(u.getHrApprovalStatus())
                .universityId(u.getUniversityId())
                .active(u.isActive())
                .emailVerified(u.isEmailVerified())
                .profileCompleted(u.isProfileCompleted())
                .profilePhotoUrl(u.getProfilePhotoUrl())
                .createdAt(u.getCreatedAt())
                .lastLoginAt(u.getLastLoginAt())
                .build();
    }

    private InternResponseDTO toInternResponseDTO(User u) {
        InternResponseDTO dto = InternResponseDTO.builder().build();
        dto.setId(u.getId());
        dto.setFirstName(u.getFirstName());
        dto.setLastName(u.getLastName());
        dto.setEmail(u.getEmail());
        dto.setRole(u.getRole());
        dto.setHrApprovalStatus(u.getHrApprovalStatus());
        dto.setUniversityId(u.getUniversityId());
        dto.setActive(u.isActive());
        dto.setEmailVerified(u.isEmailVerified());
        dto.setProfileCompleted(u.isProfileCompleted());
        dto.setProfilePhotoUrl(u.getProfilePhotoUrl());
        dto.setCreatedAt(u.getCreatedAt());
        dto.setLastLoginAt(u.getLastLoginAt());
        String supervisorName = null;
        Optional<InternProfile> ipOpt = internProfileRepository.findByUser_Id(u.getId());
        if (ipOpt.isPresent() && ipOpt.get().getSupervisorUserId() != null) {
            supervisorName = userRepository.findById(ipOpt.get().getSupervisorUserId())
                    .map(sup -> sup.getFirstName() + " " + sup.getLastName())
                    .orElse(null);
        }
        Double attendanceRate = null;
        Double performanceScore = null;
        LocalDate now = AttendanceCalculationService.todayKigali();
        YearMonth ym = YearMonth.from(now);
        long expectedWorkdays = ipOpt.map(ip -> InternshipAttendanceRules.countExpectedWorkdaysInMonth(ip, ym)).orElse(0L);
        List<Attendance> att = attendanceRepository.findForUserInDateRange(u.getId(), ym.atDay(1), ym.atEndOfMonth());
        long countedDays = att.stream()
                .filter(a -> InternshipAttendanceRules.isWorkday(a.getAttendanceDate()))
                .filter(a -> ipOpt.isPresent() && InternshipAttendanceRules.isWithinContract(ipOpt.get(), a.getAttendanceDate()))
                .filter(a -> a.getStatus() != Attendance.AttendanceStatus.ABSENT && a.getStatus() != Attendance.AttendanceStatus.PENDING)
                .count();
        attendanceRate = expectedWorkdays > 0 ? (countedDays * 100.0 / expectedWorkdays) : 0.0;
        performanceScore = performanceScoreRepository.findByIntern_IdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, 1))
                .stream().findFirst().map(PerformanceScore::getOverallScore).orElse(null);
        dto.setSupervisorName(supervisorName);
        dto.setAttendanceRate(attendanceRate);
        dto.setPerformanceScore(performanceScore);
        if (ipOpt.isPresent()) {
            InternProfile ip = ipOpt.get();
            dto.setInternshipStartDate(ip.getInternshipStartDate());
            dto.setInternshipEndDate(ip.getInternshipEndDate());
            dto.setInternshipStatus(InternshipAttendanceRules.computeInternshipStatus(ip, now));
        } else {
            dto.setInternshipStatus("NO_DATES");
        }
        return dto;
    }

    private SupervisorResponseDTO toSupervisorResponseDTO(User u) {
        SupervisorResponseDTO dto = SupervisorResponseDTO.builder().build();
        dto.setId(u.getId());
        dto.setFirstName(u.getFirstName());
        dto.setLastName(u.getLastName());
        dto.setEmail(u.getEmail());
        dto.setRole(u.getRole());
        dto.setHrApprovalStatus(u.getHrApprovalStatus());
        dto.setUniversityId(u.getUniversityId());
        dto.setActive(u.isActive());
        dto.setEmailVerified(u.isEmailVerified());
        dto.setProfileCompleted(u.isProfileCompleted());
        dto.setProfilePhotoUrl(u.getProfilePhotoUrl());
        dto.setCreatedAt(u.getCreatedAt());
        dto.setLastLoginAt(u.getLastLoginAt());
        int count = internProfileRepository.findBySupervisorUserId(u.getId()).size();
        dto.setAssignedInternsCount(count);
        return dto;
    }

    private String initials(String firstName, String lastName) {
        String f = firstName != null && !firstName.isBlank() ? firstName.substring(0, 1).toUpperCase() : "";
        String l = lastName != null && !lastName.isBlank() ? lastName.substring(0, 1).toUpperCase() : "";
        String out = (f + l).trim();
        return out.isEmpty() ? "IN" : out;
    }
}
