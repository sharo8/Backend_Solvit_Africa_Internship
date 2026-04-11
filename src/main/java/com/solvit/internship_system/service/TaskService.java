package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.tasks.CreateTaskRequestDto;
import com.solvit.internship_system.dto.tasks.PatchTaskStatusRequestDto;
import com.solvit.internship_system.dto.tasks.TaskAssignmentMode;
import com.solvit.internship_system.dto.tasks.TaskDto;
import com.solvit.internship_system.dto.tasks.TasksCreatedResponseDto;
import com.solvit.internship_system.dto.tasks.UpdateTaskProgressRequestDto;
import com.solvit.internship_system.dto.tasks.UpdateTaskRequestDto;
import com.solvit.internship_system.dto.tasks.UserSummaryDto;
import com.solvit.internship_system.entity.Attendance;
import com.solvit.internship_system.entity.Project;
import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.Task;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.Notification;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.AttendanceRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.ProjectRepository;
import com.solvit.internship_system.repository.TaskRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectGroupService projectGroupService;
    private final AttendanceRepository attendanceRepository;
    private final InternProfileRepository internProfileRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AiInsightsService aiInsightsService;

    @Transactional
    public TasksCreatedResponseDto create(CreateTaskRequestDto dto, Long assignerId) {
        User assigner = userRepository.findById(assignerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assignerId));

        if (dto.getProjectId() == null) {
            throw new BadRequestException("projectId is required");
        }
        Project project = projectRepository.findByIdWithGroupContext(dto.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", dto.getProjectId()));

        User supervisorUser = userRepository.findById(dto.getSupervisorId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getSupervisorId()));
        if (supervisorUser.getRole() != Role.SUPERVISOR && supervisorUser.getRole() != Role.ADMIN) {
            throw new BadRequestException("supervisorId must reference a user with role SUPERVISOR or ADMIN");
        }

        assertSupervisorMatchesProject(supervisorUser, project);
        if (assigner.getRole() == Role.SUPERVISOR) {
            validateSupervisorAccess(assigner.getId(), supervisorUser.getId());
        }

        TaskAssignmentMode mode = dto.getAssignmentMode() != null ? dto.getAssignmentMode() : TaskAssignmentMode.INDIVIDUAL;

        if (dto.getDueDate().isBefore(AttendanceCalculationService.todayKigali())) {
            throw new BadRequestException("Due date cannot be in the past when creating");
        }

        if (mode == TaskAssignmentMode.GROUP_COHORT && dto.getDependsOnTaskId() != null) {
            throw new BadRequestException("Task dependencies are not supported for cohort-wide (group) assignment");
        }

        Task dependsOnTemplate = null;
        if (dto.getDependsOnTaskId() != null) {
            dependsOnTemplate = taskRepository.findById(dto.getDependsOnTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task", dto.getDependsOnTaskId()));
        }

        if (mode == TaskAssignmentMode.GROUP_COHORT) {
            if (dto.getGroupId() == null) {
                throw new BadRequestException("groupId is required for GROUP_COHORT assignment");
            }
            if (dto.getAssigneeId() != null) {
                throw new BadRequestException("Do not set assigneeId for GROUP_COHORT; each intern in the cohort receives a task");
            }
            ProjectGroup cohort = projectGroupService.getActiveGroupWithInterns(dto.getGroupId());
            if (project != null && project.getGroup() != null
                    && !Objects.equals(project.getGroup().getId(), cohort.getId())) {
                throw new BadRequestException("Selected project must belong to the same cohort as the assignment");
            }
            if (cohort.getSupervisor() == null) {
                throw new BadRequestException("The cohort must have an assigned supervisor before group tasks can be created");
            }
            if (!Objects.equals(cohort.getSupervisor().getId(), supervisorUser.getId())) {
                throw new BadRequestException("supervisorId must match the cohort supervisor");
            }
            boolean canCreate = assigner.getRole() == Role.ADMIN
                    || assigner.getRole() == Role.HR
                    || (cohort.getSupervisor() != null && cohort.getSupervisor().getId().equals(assignerId));
            if (!canCreate) {
                throw new AccessDeniedException("Only admin, HR, or the cohort supervisor can create cohort tasks");
            }
            if (cohort.getInterns() == null || cohort.getInterns().isEmpty()) {
                throw new BadRequestException("Cohort has no interns to assign tasks to");
            }

            String batchId = UUID.randomUUID().toString();
            List<TaskDto> created = new ArrayList<>();
            for (User assignee : cohort.getInterns()) {
                if (assignee.getRole() != Role.INTERN) {
                    continue;
                }
                assertAssigneeForProject(project, cohort, assignee);
                assertNoDuplicateTitle(dto.getTitle(), assignee.getId(), dto.getProjectId());
                syncInternSupervisorLink(assignee.getId(), supervisorUser.getId());
                Task task = buildNewTaskEntity(dto, assigner, assignee, supervisorUser, project, cohort, batchId, null);
                Task saved = taskRepository.save(task);
                sendTaskAssignedEmail(saved);
                created.add(toDto(saved));
            }
            if (created.isEmpty()) {
                throw new BadRequestException("No interns found in this cohort");
            }
            return TasksCreatedResponseDto.builder().tasks(created).build();
        }

        if (dto.getAssigneeId() == null) {
            throw new BadRequestException("assigneeId is required for individual assignment");
        }

        User assignee = userRepository.findById(dto.getAssigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getAssigneeId()));
        if (assignee.getRole() != Role.INTERN) {
            throw new BadRequestException("Task can only be assigned to an INTERN");
        }
        if (assigner.getRole() == Role.SUPERVISOR) {
            validateInternBelongsToSupervisor(assignee.getId(), supervisorUser.getId());
        }

        if (project != null) {
            if (project.getGroup() != null) {
                ProjectGroup group = project.getGroup();
                boolean canCreateInProject = assigner.getRole() == Role.ADMIN
                        || assigner.getRole() == Role.HR
                        || (group.getSupervisor() != null && group.getSupervisor().getId().equals(assignerId));
                if (!canCreateInProject) {
                    throw new AccessDeniedException("Only the group supervisor, HR, or an admin can create tasks in this project");
                }
                assertAssigneeForProject(project, group, assignee);
            } else {
                boolean canLinkStandalone = assigner.getRole() == Role.ADMIN
                        || assigner.getRole() == Role.HR
                        || assigner.getRole() == Role.SUPERVISOR;
                if (!canLinkStandalone) {
                    throw new AccessDeniedException("Only admin, HR, or a supervisor can create tasks linked to this project");
                }
            }
        }

        assertNoDuplicateTitle(dto.getTitle(), assignee.getId(), dto.getProjectId());

        if (dependsOnTemplate != null) {
            if (!Objects.equals(dependsOnTemplate.getAssignee().getId(), assignee.getId())) {
                throw new BadRequestException("Prerequisite task must belong to the same intern");
            }
        }

        syncInternSupervisorLink(assignee.getId(), supervisorUser.getId());
        Task task = buildNewTaskEntity(dto, assigner, assignee, supervisorUser, project, null, null, dependsOnTemplate);
        Task saved = taskRepository.save(task);
        sendTaskAssignedEmail(saved);
        return TasksCreatedResponseDto.builder().tasks(List.of(toDto(saved))).build();
    }

    /**
     * Keep intern-supervisor assignment consistent with task assignment flow so supervisor dashboards
     * and management counters are updated immediately.
     */
    private void syncInternSupervisorLink(Long internId, Long supervisorId) {
        if (internId == null || supervisorId == null) {
            return;
        }
        InternProfile profile = internProfileRepository.findByUser_Id(internId)
                .orElseGet(() -> {
                    User intern = userRepository.findById(internId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", internId));
                    return InternProfile.builder().user(intern).build();
                });
        if (!Objects.equals(profile.getSupervisorUserId(), supervisorId)) {
            profile.setSupervisorUserId(supervisorId);
            internProfileRepository.save(profile);
        }
    }

    private void assertSupervisorMatchesProject(User supervisorUser, Project project) {
        User expected = project.getSupervisor();
        if (expected == null && project.getGroup() != null) {
            expected = project.getGroup().getSupervisor();
        }
        if (expected == null) {
            return;
        }
        if (!Objects.equals(expected.getId(), supervisorUser.getId())) {
            throw new AccessDeniedException("Selected project does not belong to this supervisor");
        }
    }

    private void validateSupervisorAccess(Long currentUserId, Long supervisorId) {
        if (!Objects.equals(currentUserId, supervisorId)) {
            throw new AccessDeniedException("You can only create tasks as yourself");
        }
    }

    private void validateInternBelongsToSupervisor(Long internId, Long supervisorId) {
        InternProfile ip = internProfileRepository.findByUser_Id(internId)
                .orElseThrow(() -> new BadRequestException("Intern profile not found"));
        if (!Objects.equals(ip.getSupervisorUserId(), supervisorId)) {
            throw new AccessDeniedException("This intern is not under your supervision");
        }
    }

    private void assertAssigneeForProject(Project project, ProjectGroup group, User assignee) {
        if (project == null) {
            return;
        }
        if (project.getGroup() == null) {
            return;
        }
        boolean inGroup = group != null && group.getInterns().stream().anyMatch(u -> u.getId().equals(assignee.getId()));
        if (!inGroup) {
            throw new BadRequestException("Assignee must be a member of the project's group");
        }
        boolean onProject = project.getAssignedInterns().stream().anyMatch(u -> u.getId().equals(assignee.getId()));
        if (!onProject) {
            throw new BadRequestException("Assignee must be assigned to the project");
        }
    }

    private void assertNoDuplicateTitle(String title, Long assigneeId, Long projectId) {
        boolean duplicate = projectId == null
                ? taskRepository.existsByActiveTrueAndTitleIgnoreCaseAndAssignee_IdAndProject_IsNull(title, assigneeId)
                : taskRepository.existsByActiveTrueAndTitleIgnoreCaseAndAssignee_IdAndProject_Id(title, assigneeId, projectId);
        if (duplicate) {
            throw new BadRequestException("Duplicate task title for the same intern on the same project is not allowed");
        }
    }

    private Task buildNewTaskEntity(
            CreateTaskRequestDto dto,
            User assigner,
            User assignee,
            User supervisorUser,
            Project project,
            ProjectGroup cohortGroup,
            String groupBatchId,
            Task dependsOn
    ) {
        String desc = dto.getDescription() != null ? dto.getDescription().trim() : null;
        if (desc != null && desc.length() > 2000) {
            desc = desc.substring(0, 2000);
        }
        String instr = dto.getInstructions() != null ? dto.getInstructions().trim() : null;
        if (instr != null && instr.length() > 5000) {
            instr = instr.substring(0, 5000);
        }
        String evType = dto.getEvidenceType() != null ? dto.getEvidenceType().trim().toUpperCase() : null;
        if (evType != null && evType.length() > 32) {
            evType = evType.substring(0, 32);
        }
        return Task.builder()
                .title(dto.getTitle().trim())
                .description(desc)
                .instructions(instr)
                .dueDate(dto.getDueDate())
                .priority(dto.getPriority() != null ? dto.getPriority() : Task.TaskPriority.MEDIUM)
                .assignee(assignee)
                .assigner(assigner)
                .supervisor(supervisorUser)
                .project(project)
                .cohortGroup(cohortGroup)
                .groupAssignmentBatchId(groupBatchId)
                .dependsOn(dependsOn)
                .status(Task.TaskStatus.PENDING)
                .progressPercent(0)
                .timeLoggedSeconds(0)
                .estimatedHours(dto.getEstimatedHours())
                .expectedEvidenceType(evType)
                .submissionCount(0)
                .feedbackNote(null)
                .emailSent(false)
                .dueTomorrowReminderSent(false)
                .overdueAlertSent(false)
                .inReviewEmailSent(false)
                .completedEmailSent(false)
                .cancelledEmailSent(false)
                .active(true)
                .build();
    }

    private void assertSubmitForReviewRules(Task t) {
        if (t.getDependsOn() != null) {
            Task dep = taskRepository.findById(t.getDependsOn().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task", t.getDependsOn().getId()));
            if (dep.getStatus() != Task.TaskStatus.VALIDATED) {
                throw new BadRequestException("The prerequisite task must be validated before you can submit this task for review");
            }
        }
        Attendance att = attendanceRepository.findByUser_IdAndAttendanceDate(t.getAssignee().getId(), AttendanceCalculationService.todayKigali())
                .orElse(null);
        if (att == null || att.getCheckInAt() == null) {
            throw new BadRequestException("A check-in is required today before submitting this task for review");
        }
    }

    @Transactional(readOnly = true)
    public TaskDto getByIdForActor(Long id, Long userId, Role role) {
        Task t = taskRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id));
        User viewer = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Role dbRole = viewer.getRole();
        if (role != null && role != dbRole) {
            log.warn(
                    "[tasks] getById userId={}: SecurityContext role {} differs from database role {} — using database role for access check",
                    userId,
                    role,
                    dbRole);
        }
        assertCanViewTask(t, userId, dbRole);
        return toDto(t);
    }

    @Transactional(readOnly = true)
    public Page<TaskDto> getByAssignee(Long assigneeId, Pageable pageable) {
        return taskRepository.findByActiveTrueAndAssignee_IdOrderByCreatedAtDesc(assigneeId, pageable).map(this::toDto);
    }

    private static final int MIN_COMPLETION_EVIDENCE_LENGTH = 10;

    @Transactional
    public TaskDto updateProgress(Long taskId, UpdateTaskProgressRequestDto body, Long userId) {
        Task t = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        requireAssignee(t, userId);
        if (body == null || body.getProgressPercent() == null) {
            throw new BadRequestException("progressPercent is required");
        }
        int progressPercent = Math.max(0, Math.min(100, body.getProgressPercent()));
        if (t.getStatus() == Task.TaskStatus.VALIDATED || t.getStatus() == Task.TaskStatus.CANCELLED) {
            throw new BadRequestException("Cannot update progress on a completed or cancelled task");
        }

        String evidence = body.getCompletionEvidence() != null ? body.getCompletionEvidence().trim() : null;
        if (evidence != null && evidence.length() > 4000) {
            evidence = evidence.substring(0, 4000);
        }
        if (evidence != null && !evidence.isEmpty()) {
            t.setCompletionEvidence(evidence);
        }

        Integer oldProgress = t.getProgressPercent();
        t.setProgressPercent(progressPercent);

        if (t.getStatus() == Task.TaskStatus.PENDING && progressPercent > 0) {
            t.setStatus(Task.TaskStatus.IN_PROGRESS);
            if (t.getStartedAt() == null) {
                t.setStartedAt(Instant.now());
            }
        } else if (progressPercent == 0 && t.getStatus() == Task.TaskStatus.IN_PROGRESS) {
            t.setStatus(Task.TaskStatus.PENDING);
        }

        Task saved = taskRepository.save(t);
        if (!Objects.equals(oldProgress, progressPercent) && !saved.isEmailSent()) {
            saved.setEmailSent(true);
            taskRepository.save(saved);
        }
        return toDto(saved);
    }

    /** Intern adds seconds from daily work timer (max 24h per request). */
    @Transactional
    public TaskDto logTime(Long taskId, int additionalSeconds, Long userId) {
        Task t = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        requireAssignee(t, userId);
        if (additionalSeconds <= 0 || additionalSeconds > 86400) {
            throw new IllegalArgumentException("additionalSeconds must be greater than 0 and at most 86400");
        }
        int cur = t.getTimeLoggedSeconds() != null ? t.getTimeLoggedSeconds() : 0;
        t.setTimeLoggedSeconds(cur + additionalSeconds);
        if (t.getStartedAt() == null) {
            t.setStartedAt(Instant.now());
        }
        return toDto(taskRepository.save(t));
    }

    /**
     * Intern: PENDING/REJECTED/OVERDUE → IN_PROGRESS where applicable; work → IN_REVIEW (submit).
     * Supervisor/Admin/HR: IN_REVIEW → IN_PROGRESS (rework; feedbackNote required).
     * Admin/Supervisor: cancel to CANCELLED. Formal rejection with status REJECTED uses POST /reject.
     */
    @Transactional
    public TaskDto patchStatus(Long taskId, PatchTaskStatusRequestDto dto, Long userId) {
        if (dto == null || dto.getStatus() == null) {
            throw new BadRequestException("status is required");
        }
        Task t = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        User actor = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Task.TaskStatus target = dto.getStatus();

        if (target == Task.TaskStatus.OVERDUE) {
            throw new BadRequestException("OVERDUE is set only by the system scheduler");
        }

        if (target == Task.TaskStatus.VALIDATED) {
            throw new BadRequestException("Use POST /api/tasks/{id}/validate to approve a task");
        }

        if (target == Task.TaskStatus.REJECTED) {
            throw new BadRequestException("Use POST /api/tasks/{id}/reject to reject a submission");
        }

        if (target == Task.TaskStatus.CANCELLED) {
            if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPERVISOR) {
                throw new AccessDeniedException("Only ADMIN or SUPERVISOR can cancel a task");
            }
            if (t.getStatus() == Task.TaskStatus.VALIDATED) {
                throw new BadRequestException("Cannot cancel a validated task");
            }
            t.setStatus(Task.TaskStatus.CANCELLED);
            t.setActive(false);
            Task saved = taskRepository.save(t);
            if (!saved.isCancelledEmailSent()) {
                sendTaskCancelledEmail(saved);
            }
            return toDto(saved);
        }

        if (target == Task.TaskStatus.IN_PROGRESS && t.getStatus() == Task.TaskStatus.PENDING) {
            if (actor.getRole() != Role.INTERN || !Objects.equals(t.getAssignee().getId(), userId)) {
                throw new AccessDeniedException("Only the assignee can start this task");
            }
            t.setStartedAt(Instant.now());
            t.setStatus(Task.TaskStatus.IN_PROGRESS);
            return toDto(taskRepository.save(t));
        }

        if (target == Task.TaskStatus.IN_PROGRESS && t.getStatus() == Task.TaskStatus.REJECTED) {
            if (actor.getRole() != Role.INTERN || !Objects.equals(t.getAssignee().getId(), userId)) {
                throw new AccessDeniedException("Only the assignee can resume work after a rejection");
            }
            if (t.getStartedAt() == null) {
                t.setStartedAt(Instant.now());
            }
            t.setStatus(Task.TaskStatus.IN_PROGRESS);
            return toDto(taskRepository.save(t));
        }

        if (target == Task.TaskStatus.IN_REVIEW
                && (t.getStatus() == Task.TaskStatus.IN_PROGRESS
                || t.getStatus() == Task.TaskStatus.OVERDUE
                || t.getStatus() == Task.TaskStatus.REJECTED)) {
            if (actor.getRole() != Role.INTERN || !Objects.equals(t.getAssignee().getId(), userId)) {
                throw new AccessDeniedException("Only the assignee can submit for review");
            }
            String ev = dto.getCompletionEvidence() != null ? dto.getCompletionEvidence().trim() : null;
            if (ev == null || ev.length() < MIN_COMPLETION_EVIDENCE_LENGTH) {
                if (t.getCompletionEvidence() == null || t.getCompletionEvidence().trim().length() < MIN_COMPLETION_EVIDENCE_LENGTH) {
                    throw new BadRequestException(
                            "completionEvidence is required when submitting for review (at least " + MIN_COMPLETION_EVIDENCE_LENGTH + " characters)");
                }
            } else {
                t.setCompletionEvidence(ev);
            }
            if (dto.getEvidenceUrl() != null && !dto.getEvidenceUrl().isBlank()) {
                t.setEvidenceUrl(dto.getEvidenceUrl().trim());
            }
            if (dto.getEvidenceNotes() != null && !dto.getEvidenceNotes().isBlank()) {
                String notes = dto.getEvidenceNotes().trim();
                t.setEvidenceNotes(notes.length() > 2000 ? notes.substring(0, 2000) : notes);
            }
            assertSubmitForReviewRules(t);
            int subs = t.getSubmissionCount() != null ? t.getSubmissionCount() : 0;
            t.setSubmissionCount(subs + 1);
            t.setProgressPercent(100);
            t.setCompletedAt(Instant.now());
            t.setStatus(Task.TaskStatus.IN_REVIEW);
            Task saved = taskRepository.save(t);
            notifySupervisorTaskSubmitted(saved);
            if (!saved.isCompletedEmailSent()) {
                sendTaskSubmittedForReviewEmail(saved);
            }
            return toDto(saved);
        }

        if (target == Task.TaskStatus.IN_PROGRESS && t.getStatus() == Task.TaskStatus.IN_REVIEW) {
            if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPERVISOR && actor.getRole() != Role.HR) {
                throw new AccessDeniedException("Only Supervisor, HR, or Admin can send a task back for rework");
            }
            String note = dto.getFeedbackNote();
            if (note == null || note.isBlank()) {
                throw new BadRequestException("feedbackNote is required when sending a task back for rework");
            }
            t.setFeedbackNote(note.trim());
            t.setSupervisorComment(note.trim());
            t.setStatus(Task.TaskStatus.IN_PROGRESS);
            t.setProgressPercent(Math.min(t.getProgressPercent() != null ? t.getProgressPercent() : 0, 99));
            Task saved = taskRepository.save(t);
            sendTaskUpdatedEmail(saved);
            return toDto(saved);
        }

        throw new BadRequestException("Unsupported status transition from " + t.getStatus() + " to " + target);
    }

    @Transactional
    public TaskDto update(Long taskId, UpdateTaskRequestDto dto, Long userId) {
        Task t = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        User editor = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (editor.getRole() != Role.ADMIN
                && editor.getRole() != Role.SUPERVISOR
                && editor.getRole() != Role.HR) {
            throw new BadRequestException("Only Supervisor, HR, or Admin can update tasks");
        }

        Integer oldProgress = t.getProgressPercent();
        String oldFeedback = t.getFeedbackNote();

        if (dto.getTitle() != null) {
            t.setTitle(dto.getTitle().trim());
        }
        if (dto.getDescription() != null) {
            t.setDescription(dto.getDescription());
        }
        if (dto.getPriority() != null) {
            t.setPriority(dto.getPriority());
        }
        if (dto.getDueDate() != null) {
            if (dto.getDueDate().isBefore(AttendanceCalculationService.todayKigali())) {
                throw new BadRequestException("Due date cannot be in the past");
            }
            t.setDueDate(dto.getDueDate());
        }
        if (dto.getProjectId() != null) {
            Project p = projectRepository.findByIdWithGroupContext(dto.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project", dto.getProjectId()));
            if (p.getGroup() != null) {
                boolean canEdit = editor.getRole() == Role.ADMIN
                        || editor.getRole() == Role.HR
                        || (p.getGroup().getSupervisor() != null
                        && p.getGroup().getSupervisor().getId().equals(userId));
                if (!canEdit) {
                    throw new AccessDeniedException("Only the group supervisor, HR, or an admin can link tasks to this project");
                }
                User assignee = t.getAssignee();
                boolean inGroup = p.getGroup().getInterns().stream().anyMatch(u -> u.getId().equals(assignee.getId()));
                if (!inGroup) {
                    throw new BadRequestException("Task assignee must be a member of the project's group");
                }
                boolean onProject = p.getAssignedInterns().stream().anyMatch(u -> u.getId().equals(assignee.getId()));
                if (!onProject) {
                    throw new BadRequestException("Task assignee must be assigned to the project");
                }
            }
            t.setProject(p);
        }

        if (dto.getProgressPercent() != null) {
            if (dto.getProgressPercent() < 0 || dto.getProgressPercent() > 100) {
                throw new BadRequestException("progressPercent must be between 0 and 100");
            }
            t.setProgressPercent(dto.getProgressPercent());
        }

        if (dto.getFeedbackNote() != null) {
            t.setFeedbackNote(dto.getFeedbackNote());
        }

        Task saved = taskRepository.save(t);

        if (!Objects.equals(oldProgress, saved.getProgressPercent())) {
            sendTaskUpdatedEmail(saved);
        }

        if (dto.getFeedbackNote() != null && !Objects.equals(oldFeedback, dto.getFeedbackNote())
                && dto.getFeedbackNote().trim().length() > 0) {
            sendTaskFeedbackEmail(saved);
        }
        return toDto(saved);
    }

    @Transactional
    public TaskDto validate(Long taskId, Long supervisorId) {
        User supervisor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", supervisorId));
        if (supervisor.getRole() != Role.ADMIN && supervisor.getRole() != Role.SUPERVISOR && supervisor.getRole() != Role.HR) {
            throw new AccessDeniedException("Only Supervisor, HR, or Admin can validate tasks");
        }

        Task t = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (t.getStatus() != Task.TaskStatus.IN_REVIEW) {
            throw new BadRequestException("Only tasks in IN_REVIEW can be validated");
        }
        if (t.getSupervisor() != null && supervisor.getRole() != Role.ADMIN && supervisor.getRole() != Role.HR) {
            if (!Objects.equals(t.getSupervisor().getId(), supervisorId)) {
                throw new AccessDeniedException("Only the assigned supervisor can validate this task");
            }
        }

        Task.TaskStatus old = t.getStatus();
        t.setValidatedBy(supervisorId);
        t.setValidatedAt(Instant.now());
        t.setStatus(Task.TaskStatus.VALIDATED);
        Task saved = taskRepository.save(t);
        if (old != Task.TaskStatus.VALIDATED) {
            sendTaskValidatedEmail(saved);
        }
        try {
            aiInsightsService.recomputePerformanceScoreForIntern(t.getAssignee().getId());
        } catch (Exception ex) {
            log.warn("[tasks] Could not recompute performance score for intern {}: {}", t.getAssignee().getId(), ex.getMessage());
        }
        return toDto(saved);
    }

    @Transactional
    public TaskDto reject(Long taskId, String reason, Long supervisorId) {
        User actor = userRepository.findById(supervisorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", supervisorId));
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPERVISOR) {
            throw new AccessDeniedException("Only Supervisor or Admin can reject a task submission");
        }
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (task.getStatus() != Task.TaskStatus.IN_REVIEW) {
            throw new BadRequestException("Can only reject tasks that are awaiting review (IN_REVIEW)");
        }
        if (task.getSupervisor() != null && actor.getRole() == Role.SUPERVISOR) {
            if (!Objects.equals(task.getSupervisor().getId(), supervisorId)) {
                throw new AccessDeniedException("Only the assigned supervisor can reject this task");
            }
            validateInternBelongsToSupervisor(task.getAssignee().getId(), supervisorId);
        }
        String r = reason != null ? reason.trim() : "";
        if (r.isEmpty()) {
            throw new BadRequestException("reason is required");
        }
        if (r.length() > 1000) {
            r = r.substring(0, 1000);
        }
        task.setStatus(Task.TaskStatus.REJECTED);
        task.setRejectionReason(r);
        task.setSupervisorComment(r);
        task.setProgressPercent(Math.min(task.getProgressPercent() != null ? task.getProgressPercent() : 0, 99));
        Task saved = taskRepository.save(task);
        notifyInternTaskRejected(saved);
        sendTaskUpdatedEmail(saved);
        return toDto(saved);
    }

    @Transactional
    public void softDelete(Long taskId, Long userId) {
        Task t = taskRepository.findById(taskId).orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        User editor = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (editor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN can delete (cancel) tasks");
        }
        t.setStatus(Task.TaskStatus.CANCELLED);
        t.setActive(false);
        taskRepository.save(t);
        if (!t.isCancelledEmailSent()) {
            sendTaskCancelledEmail(t);
        }
    }

    /**
     * Task list/stats scope (see also {@link com.solvit.internship_system.controller.TaskController} for write rules):
     * <ul>
     * <li>{@link Role#ADMIN} — {@code null} scope: global view, all active tasks (unfiltered).</li>
     * <li>{@link Role#HR} — {@code null} scope: same global read visibility for oversight; task mutations are ADMIN/SUPERVISOR only.</li>
     * <li>{@link Role#SUPERVISOR} — scope = supervisor user id: only tasks linked to that supervisor (assigner / project cohort / task.supervisor).</li>
     * <li>{@link Role#INTERN} — uses assignee-scoped stats/list paths, not this supervisor filter.</li>
     * </ul>
     * Uses the user's role from the database (not only SecurityContext) so ADMIN is never scoped like a supervisor by mistake.
     */
    private Long supervisorScopeForTaskQueries(Long viewerUserId, Role securityContextRole, String logContext) {

        if (viewerUserId == null) {
            log.error("[tasks] {} viewerUserId is NULL → returning NO DATA for safety", logContext);
            return -1L; // force empty result instead of leaking data
        }

        User viewer = userRepository.findById(viewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", viewerUserId));

        Role dbRole = viewer.getRole();

        if (securityContextRole != null && securityContextRole != dbRole) {
            log.warn(
                    "[tasks] userId={}: SecurityContext role {} differs from database role {} — using DB role",
                    viewerUserId,
                    securityContextRole,
                    dbRole
            );
        }

        Long scopeId;
        if (dbRole == Role.ADMIN || dbRole == Role.HR) {
            scopeId = null;
        } else if (dbRole == Role.SUPERVISOR) {
            scopeId = viewerUserId;
        } else {
            scopeId = viewerUserId;
        }

        log.debug(
                "[supervisorScope] context={} viewerUserId={} dbUser id={} email={} dbRole={} securityContextRole={} → scopeId={}",
                logContext,
                viewerUserId,
                viewer.getId(),
                viewer.getEmail(),
                dbRole,
                securityContextRole,
                scopeId);

        if (dbRole == Role.ADMIN || dbRole == Role.HR) {
            return null;
        }
        if (dbRole == Role.SUPERVISOR) {
            return viewerUserId;
        }
        return viewerUserId;
    }

    @Transactional(readOnly = true)
    public Page<TaskDto> listFiltered(String status, Long assignedTo, Long projectId, Pageable pageable, Long viewerUserId, Role viewerRole) {
        Set<Task.TaskStatus> statuses = parseFilterStatuses(status);
        Long supervisorScope = supervisorScopeForTaskQueries(viewerUserId, viewerRole, "listFiltered");
        logDebugDbTaskCounts();
        log.debug(
                "[DEBUG-QUERY] assignedTo={} projectId={} rawStatusParam={} parsedStatuses={} supervisorScopeId={} page={} size={}",
                assignedTo,
                projectId,
                status,
                statuses,
                supervisorScope,
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<Task> raw;
        if (statuses == null) {
            log.debug(
                    "[TaskService.search] params: assignedTo={} projectId={} status={} supervisorScopeId={} page={} size={} (branch=no-status-filter)",
                    assignedTo,
                    projectId,
                    null,
                    supervisorScope,
                    pageable.getPageNumber(),
                    pageable.getPageSize());
            raw = taskRepository.searchFiltered(assignedTo, projectId, null, supervisorScope, pageable);
        } else if (statuses.size() == 1) {
            Task.TaskStatus single = statuses.iterator().next();
            log.debug(
                    "[TaskService.search] params: assignedTo={} projectId={} status={} supervisorScopeId={} page={} size={} (branch=single-status)",
                    assignedTo,
                    projectId,
                    single,
                    supervisorScope,
                    pageable.getPageNumber(),
                    pageable.getPageSize());
            raw = taskRepository.searchFiltered(assignedTo, projectId, single, supervisorScope, pageable);
        } else {
            log.debug(
                    "[TaskService.search] params: assignedTo={} projectId={} statuses={} supervisorScopeId={} page={} size={} (branch=multi-status)",
                    assignedTo,
                    projectId,
                    statuses,
                    supervisorScope,
                    pageable.getPageNumber(),
                    pageable.getPageSize());
            raw = taskRepository.searchFilteredStatuses(assignedTo, projectId, statuses, supervisorScope, pageable);
        }
        log.debug(
                "[TaskService.search] result: totalElements={} returnedInPage={}",
                raw.getTotalElements(),
                raw.getContent().size());
        return raw.map(this::toDto);
    }

    /**
     * Raw table counts for support: confirms whether the app’s database actually has task rows and how many are {@code active=true}
     * (the list API only returns active tasks).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> adminTaskTableDiagnostics() {
        long totalRows = taskRepository.count();
        long activeTrue = taskRepository.countByActiveTrue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tasksTableRowCount", totalRows);
        out.put("activeTrueCount", activeTrue);
        out.put("debugNativeTotalRows", taskRepository.debugCountAllTasks());
        out.put("debugNativeActiveOneOrTrue", taskRepository.debugCountActiveTasks());
        out.put("debugDistinctActiveColumnValues", taskRepository.debugDistinctActiveValues());
        if (totalRows > 0 && activeTrue == 0) {
            out.put(
                    "hint",
                    "The tasks table has rows but none with active=true. The API only lists active tasks. Check the active column or cancelled rows.");
        } else if (totalRows == 0) {
            out.put(
                    "hint",
                    "No rows in tasks — this app instance is using an empty database, or tasks were never committed. Verify spring.datasource.url (host, port, database name) matches the DB you use when creating tasks.");
        } else {
            out.put(
                    "hint",
                    "Active tasks exist in DB. List/stats use the database user tied to this session (email from JWT) and that user's role (ADMIN/HR see all; SUPERVISOR only linked tasks). If counts are zero, verify the token's email matches the admin account and re-login after role changes.");
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats(Long viewerUserId, Role viewerRole) {
        LocalDate today = AttendanceCalculationService.todayKigali();
        User viewer = userRepository.findById(viewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", viewerUserId));
        if (viewer.getRole() == Role.INTERN) {
            return internSelfTaskStats(viewerUserId, today);
        }
        Long scope = supervisorScopeForTaskQueries(viewerUserId, viewerRole, "stats");
        logDebugDbTaskCounts();
        log.debug(
                "[DEBUG-QUERY] stats countSearchFiltered assignedTo=null projectId=null status=null supervisorScopeId={} today={}",
                scope,
                today);
        long total = taskRepository.countSearchFiltered(null, null, null, scope);
        long inProgress = taskRepository.countSearchFiltered(null, null, Task.TaskStatus.IN_PROGRESS, scope);
        long inReview = taskRepository.countSearchFiltered(null, null, Task.TaskStatus.IN_REVIEW, scope);
        long validated = taskRepository.countSearchFiltered(null, null, Task.TaskStatus.VALIDATED, scope);
        long notStarted = taskRepository.countSearchFiltered(null, null, Task.TaskStatus.PENDING, scope);
        long overdue = taskRepository.countOverdueFiltered(
                today,
                Set.of(Task.TaskStatus.PENDING, Task.TaskStatus.IN_PROGRESS, Task.TaskStatus.OVERDUE),
                scope);
        long completed = taskRepository.countSearchFilteredStatuses(
                null,
                null,
                Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.VALIDATED),
                scope);
        long rejected = taskRepository.countSearchFiltered(null, null, Task.TaskStatus.REJECTED, scope);
        long needsAttention = taskRepository.countSearchFilteredStatuses(
                null,
                null,
                Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.OVERDUE, Task.TaskStatus.REJECTED),
                scope);
        long rawActiveNative = taskRepository.debugCountActiveTasks();
        log.debug(
                "[TaskService.stats] viewerUserId={} scope={} total={} inProgress={} inReview={} validated={} completed={} overdue={} notStarted={}",
                viewerUserId,
                scope,
                total,
                inProgress,
                inReview,
                validated,
                completed,
                overdue,
                notStarted);
        log.debug(
                "[DEBUG] Raw DB active count (native)={} but countSearchFiltered total (JPQL)={}",
                rawActiveNative,
                total);
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("notStarted", notStarted);
        out.put("inProgress", inProgress);
        out.put("submitted", inReview);
        out.put("inReview", inReview);
        out.put("validated", validated);
        out.put("completed", completed);
        out.put("overdue", overdue);
        out.put("rejected", rejected);
        out.put("needsAttention", needsAttention);
        return out;
    }

    /**
     * Task stats for the logged-in intern: filter by assignee = intern, not supervisor scope.
     */
    private Map<String, Long> internSelfTaskStats(Long internUserId, LocalDate today) {
        logDebugDbTaskCounts();
        long total = taskRepository.countSearchFiltered(internUserId, null, null, null);
        long inProgress = taskRepository.countSearchFiltered(internUserId, null, Task.TaskStatus.IN_PROGRESS, null);
        long inReview = taskRepository.countSearchFiltered(internUserId, null, Task.TaskStatus.IN_REVIEW, null);
        long validated = taskRepository.countSearchFiltered(internUserId, null, Task.TaskStatus.VALIDATED, null);
        long notStarted = taskRepository.countSearchFiltered(internUserId, null, Task.TaskStatus.PENDING, null);
        long overdue = taskRepository.countOverdueForAssignee(
                internUserId,
                today,
                Set.of(Task.TaskStatus.PENDING, Task.TaskStatus.IN_PROGRESS, Task.TaskStatus.OVERDUE));
        long completed = taskRepository.countSearchFilteredStatuses(
                internUserId,
                null,
                Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.VALIDATED),
                null);
        long rejected = taskRepository.countSearchFiltered(internUserId, null, Task.TaskStatus.REJECTED, null);
        log.info(
                "[tasks] GET /api/tasks/stats (INTERN) userId={} total={} inProgress={} completed={} overdue={} notStarted={}",
                internUserId,
                total,
                inProgress,
                completed,
                overdue,
                notStarted);
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("notStarted", notStarted);
        out.put("inProgress", inProgress);
        out.put("submitted", inReview);
        out.put("inReview", inReview);
        out.put("validated", validated);
        out.put("completed", completed);
        out.put("overdue", overdue);
        out.put("rejected", rejected);
        return out;
    }

    private void logDebugDbTaskCounts() {
        long allRows = taskRepository.debugCountAllTasks();
        long activeCount = taskRepository.debugCountActiveTasks();
        List<Object> distinctActive = taskRepository.debugDistinctActiveValues();
        log.debug(
                "[DEBUG-DB] total rows={} active=true/1 count={} distinct active column values={}",
                allRows,
                activeCount,
                distinctActive);
    }

    /**
     * Resolves list filter. {@code COMPLETED}/{@code DONE}/{@code FINISHED} match legacy task status and mean
     * “finished work” ({@link Task.TaskStatus#IN_REVIEW} + {@link Task.TaskStatus#VALIDATED}).
     */
    private Set<Task.TaskStatus> parseFilterStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        if ("COMPLETED".equals(s) || "DONE".equals(s) || "FINISHED".equals(s)) {
            return Set.of(Task.TaskStatus.IN_REVIEW, Task.TaskStatus.VALIDATED);
        }
        if ("NOT_STARTED".equals(s)) {
            return Set.of(Task.TaskStatus.PENDING);
        }
        if ("SUBMITTED".equals(s)) {
            return Set.of(Task.TaskStatus.IN_REVIEW);
        }
        String mapped = switch (s) {
            case "TODO" -> Task.TaskStatus.PENDING.name();
            default -> s;
        };
        try {
            return Set.of(Task.TaskStatus.valueOf(mapped));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown task status filter: {}", status);
            return null;
        }
    }

    private void assertCanViewTask(Task t, Long userId, Role role) {
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        if (role == Role.INTERN && t.getAssignee() != null && Objects.equals(t.getAssignee().getId(), userId)) {
            return;
        }
        if (role == Role.SUPERVISOR && supervisorLinkedToTask(t, userId)) {
            return;
        }
        throw new AccessDeniedException("You cannot access this task");
    }

    private boolean supervisorLinkedToTask(Task t, Long supervisorUserId) {
        if (t.getSupervisor() != null && Objects.equals(t.getSupervisor().getId(), supervisorUserId)) {
            return true;
        }
        if (t.getAssigner() != null && Objects.equals(t.getAssigner().getId(), supervisorUserId)) {
            return true;
        }
        return t.getProject() != null
                && t.getProject().getGroup() != null
                && t.getProject().getGroup().getSupervisor() != null
                && Objects.equals(t.getProject().getGroup().getSupervisor().getId(), supervisorUserId);
    }

    private void requireAssignee(Task t, Long userId) {
        if (!Objects.equals(t.getAssignee().getId(), userId)) {
            throw new AccessDeniedException("Only the assignee can perform this action");
        }
    }

    private void notifySupervisorTaskSubmitted(Task task) {
        User sup = task.getSupervisor() != null ? task.getSupervisor() : task.getAssigner();
        if (sup == null) {
            return;
        }
        notificationService.create(
                sup.getId(),
                "Task submitted for review",
                task.getTitle() + " — awaiting your review",
                Notification.NotificationType.SYSTEM,
                "Task",
                task.getId(),
                false
        );
    }

    private void notifyInternTaskRejected(Task task) {
        if (task.getAssignee() == null) {
            return;
        }
        String detail = task.getRejectionReason() != null ? task.getRejectionReason() : "";
        notificationService.create(
                task.getAssignee().getId(),
                "Task submission rejected",
                task.getTitle() + (detail.isEmpty() ? "" : ": " + detail),
                Notification.NotificationType.SYSTEM,
                "Task",
                task.getId(),
                false
        );
    }

    // ----------------- Email helpers -----------------

    private void sendTaskAssignedEmail(Task task) {
        if (task.getAssignee() == null || task.getAssignee().getEmail() == null) {
            return;
        }
        String subject = "📌 New Task Assigned: " + task.getTitle();
        task.setEmailSent(true);
        taskRepository.save(task);
        sendTaskMail(task.getAssignee(), task, subject, "New Task Assigned", "We wanted to keep you updated about your new assignment.");
        notificationService.create(
                task.getAssignee().getId(),
                "New Task Assigned",
                task.getTitle(),
                com.solvit.internship_system.entity.Notification.NotificationType.TASK_ASSIGNED,
                "Task",
                task.getId(),
                false
        );
    }

    private void sendTaskUpdatedEmail(Task task) {
        String subject = "🔄 Task Updated: " + task.getTitle() + " → " + task.getStatus().name();
        sendTaskMail(task.getAssignee(), task, subject, "Task Updated", "Your task status has been updated. Please review the details.");
        if (task.getAssigner() != null) {
            sendTaskMail(task.getAssigner(), task, subject, "Task Updated", "A task has been updated. Please review the details.");
        }
    }

    public void sendDueTomorrowReminder(Task task) {
        String subject = "⏰ Reminder: Task '" + task.getTitle() + "' due tomorrow";
        sendTaskMail(task.getAssignee(), task, subject, "Task Reminder", "This is a reminder that your task is due tomorrow. Please check details below.");
        if (task.getAssigner() != null) {
            sendTaskMail(task.getAssigner(), task, subject, "Task Reminder", "Reminder: a task is due tomorrow. Please review.");
        }
    }

    public void sendOverdueAlert(Task task) {
        String subject = "🚨 Overdue Task: " + task.getTitle();
        sendTaskMail(task.getAssignee(), task, subject, "Overdue Task", "This is an alert: the task is overdue. Please review details.");
        if (task.getAssigner() != null) {
            sendTaskMail(task.getAssigner(), task, subject, "Overdue Task", "Overdue alert: please review task details.");
        }
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            sendTaskMail(admin, task, subject, "Overdue Task", "Overdue alert: a task is overdue. Please review.");
        }
    }

    /** Reminder when a task is due today, still at 0% progress, and has not had this alert yet. */
    public void sendSameDayIncompleteReminder(Task task) {
        if (task.getAssignee() == null || task.getAssignee().getEmail() == null) {
            return;
        }
        String subject = "Task due today — no progress recorded: " + task.getTitle();
        sendTaskMail(task.getAssignee(), task, subject, "Task due today",
                "This task is due today and still shows no progress. Please work on it or contact your supervisor if you are blocked.");
        User sup = task.getSupervisor() != null ? task.getSupervisor() : task.getAssigner();
        if (sup != null && sup.getEmail() != null) {
            sendTaskMail(sup, task, subject, "Intern task due today",
                    "A task assigned to your intern is due today with no recorded progress yet.");
        }
        task.setSameDayIncompleteReminderSent(true);
        taskRepository.save(task);
    }

    private void sendTaskReadyForReviewEmail(Task task) {
        User notify = task.getSupervisor() != null ? task.getSupervisor() : task.getAssigner();
        if (notify == null || notify.getEmail() == null) {
            return;
        }
        task.setInReviewEmailSent(true);
        taskRepository.save(task);
        String subject = "👀 Task Ready for Review: " + task.getTitle();
        sendTaskMail(notify, task, subject, "Task Ready for Review", "A task is ready for your review.");
    }

    private void sendTaskSubmittedForReviewEmail(Task task) {
        if (task.getAssignee() == null) {
            return;
        }
        task.setCompletedEmailSent(true);
        taskRepository.save(task);
        String subject = "✅ Task submitted for review: " + task.getTitle();
        sendTaskMail(task.getAssignee(), task, subject, "Submitted for review",
                "Your task has been submitted for supervisor review.");
        sendTaskReadyForReviewEmail(task);
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            sendTaskMail(admin, task, subject, "Task submitted", "A task has been submitted for review.");
        }
    }

    private void sendTaskValidatedEmail(Task task) {
        if (task.getAssignee() == null || task.getAssignee().getEmail() == null) {
            return;
        }
        String subject = "🎉 Task validated: " + task.getTitle();
        sendTaskMail(task.getAssignee(), task, subject, "Task validated", "Your supervisor has approved this task.");
    }

    private void sendTaskCancelledEmail(Task task) {
        if (task.getAssignee() == null) {
            return;
        }
        task.setCancelledEmailSent(true);
        taskRepository.save(task);
        String subject = "❌ Task Cancelled: " + task.getTitle();
        sendTaskMail(task.getAssignee(), task, subject, "Task Cancelled", "Your task has been cancelled. Please review any notes.");
    }

    private void sendTaskFeedbackEmail(Task task) {
        if (task.getAssignee() == null) {
            return;
        }
        String subject = "💬 New Feedback on Task: " + task.getTitle();
        sendTaskMail(task.getAssignee(), task, subject, "New Feedback", "You have received new supervisor feedback. Please review details.");
    }

    private void sendTaskMail(User recipient, Task task, String subject, String header, String message) {
        if (recipient == null || recipient.getEmail() == null) {
            return;
        }
        String due = task.getDueDate() != null ? task.getDueDate().toString() : "—";
        String assignedBy = task.getAssigner() != null
                ? task.getAssigner().getFirstName() + " " + (task.getAssigner().getLastName() == null ? "" : task.getAssigner().getLastName())
                : "—";

        Map<String, Object> vars = Map.of(
                "firstName", recipient.getFirstName(),
                "header", header,
                "subject", subject,
                "taskTitle", task.getTitle(),
                "priority", task.getPriority() != null ? task.getPriority().name() : "MEDIUM",
                "dueDate", due,
                "status", task.getStatus() != null ? task.getStatus().name() : "PENDING",
                "assignedBy", assignedBy,
                "message", message,
                "ctaUrl", "http://localhost:3000/app/tasks/" + task.getId()
        );
        emailService.sendTaskMailHtml(recipient.getEmail(), subject, "emails/task-email.html", vars);
    }

    private TaskDto toDto(Task task) {
        int sec = task.getTimeLoggedSeconds() != null ? task.getTimeLoggedSeconds() : 0;
        Long daysOverdue = null;
        if (task.getStatus() == Task.TaskStatus.OVERDUE && task.getDueDate() != null) {
            daysOverdue = ChronoUnit.DAYS.between(task.getDueDate(), AttendanceCalculationService.todayKigali());
        }
        String validatedByName = null;
        if (task.getValidatedBy() != null) {
            validatedByName = userRepository.findById(task.getValidatedBy())
                    .map(u -> ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim())
                    .filter(s -> !s.isEmpty())
                    .orElse(null);
        }
        User assignee = task.getAssignee();
        String assignedToName = assignee == null
                ? null
                : ((assignee.getFirstName() != null ? assignee.getFirstName() : "") + " "
                        + (assignee.getLastName() != null ? assignee.getLastName() : "")).trim();
        String assignedToAvatar = assignee == null ? null : initials(assignee);
        User sup = task.getSupervisor();
        String supervisorName = sup == null
                ? null
                : ((sup.getFirstName() != null ? sup.getFirstName() : "") + " " + (sup.getLastName() != null ? sup.getLastName() : "")).trim();
        String groupName = task.getCohortGroup() != null ? task.getCohortGroup().getName() : null;

        return TaskDto.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .instructions(task.getInstructions())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .progressPercent(task.getProgressPercent())
                .feedbackNote(task.getFeedbackNote())
                .completionEvidence(task.getCompletionEvidence())
                .evidenceUrl(task.getEvidenceUrl())
                .evidenceNotes(task.getEvidenceNotes())
                .supervisorComment(task.getSupervisorComment())
                .rejectionReason(task.getRejectionReason())
                .estimatedHours(task.getEstimatedHours())
                .loggedSeconds(sec)
                .timeLoggedSeconds(sec)
                .submissionCount(task.getSubmissionCount() != null ? task.getSubmissionCount() : 0)
                .emailSent(task.isEmailSent())
                .assignee(assignee != null ? toUserSummary(assignee) : null)
                .assigner(task.getAssigner() != null ? toUserSummary(task.getAssigner()) : null)
                .supervisor(sup != null ? toUserSummary(sup) : null)
                .projectId(task.getProject() != null ? task.getProject().getId() : null)
                .projectTitle(task.getProject() != null ? task.getProject().getTitle() : null)
                .active(task.isActive())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .validatedAt(task.getValidatedAt())
                .validatedBy(task.getValidatedBy())
                .validatedByName(validatedByName)
                .daysOverdue(daysOverdue)
                .assignedToName(assignedToName == null || assignedToName.isEmpty() ? null : assignedToName)
                .assignedToAvatar(assignedToAvatar)
                .supervisorName(supervisorName == null || supervisorName.isEmpty() ? null : supervisorName)
                .groupName(groupName)
                .cohortGroupId(task.getCohortGroup() != null ? task.getCohortGroup().getId() : null)
                .cohortGroupName(task.getCohortGroup() != null ? task.getCohortGroup().getName() : null)
                .groupAssignmentBatchId(task.getGroupAssignmentBatchId())
                .dependsOnTaskId(task.getDependsOn() != null ? task.getDependsOn().getId() : null)
                .build();
    }

    private static String initials(User u) {
        if (u == null) {
            return null;
        }
        String a = u.getFirstName() != null && !u.getFirstName().isBlank() ? u.getFirstName().substring(0, 1).toUpperCase() : "";
        String b = u.getLastName() != null && !u.getLastName().isBlank() ? u.getLastName().substring(0, 1).toUpperCase() : "";
        String s = a + b;
        return s.isEmpty() ? "?" : s;
    }

    private UserSummaryDto toUserSummary(User u) {
        return UserSummaryDto.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .universityId(u.getUniversityId())
                .profilePhotoUrl(u.getProfilePhotoUrl())
                .role(u.getRole().name())
                .build();
    }
}
