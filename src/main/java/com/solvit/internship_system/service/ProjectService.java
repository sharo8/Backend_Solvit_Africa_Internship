package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.tasks.CreateProjectRequestDto;
import com.solvit.internship_system.dto.tasks.ProjectDto;
import com.solvit.internship_system.dto.tasks.UpdateProjectRequestDto;
import com.solvit.internship_system.dto.tasks.UserSummaryDto;
import com.solvit.internship_system.entity.Project;
import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.ProjectRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private static final int MAX_SIMULTANEOUS_PROJECTS_PER_INTERN = 3;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectGroupService projectGroupService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<ProjectDto> listActive() {
        return projectRepository.findByActiveTrue().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listActiveForSupervisor(Long supervisorId) {
        return projectRepository.findActiveForSupervisor(supervisorId, AttendanceCalculationService.todayKigali())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ProjectDto create(CreateProjectRequestDto dto, Long createdById) {
        User createdBy = userRepository.findById(createdById)
                .orElseThrow(() -> new ResourceNotFoundException("User", createdById));

        if (dto.getGroupId() == null) {
            List<Long> extra = dto.getAssignedInternIds();
            if (extra != null && !extra.isEmpty()) {
                throw new BadRequestException(
                        "Standalone projects cannot assign interns at creation. Link a cohort, or assign people later via tasks.");
            }
            validateStandaloneProjectSchedule(dto.getStartDate(), dto.getEndDate());
            if (createdBy.getRole() != Role.ADMIN && createdBy.getRole() != Role.SUPERVISOR) {
                throw new AccessDeniedException("Only an admin or supervisor can create a standalone project");
            }
            Project p = Project.builder()
                    .title(dto.getTitle().trim())
                    .description(dto.getDescription())
                    .startDate(dto.getStartDate())
                    .endDate(dto.getEndDate())
                    .status(dto.getStatus())
                    .createdBy(createdBy)
                    .group(null)
                    .supervisor(null)
                    .assignedInterns(new ArrayList<>())
                    .active(true)
                    .deadlineWarningSent(false)
                    .build();
            Project saved = projectRepository.save(p);
            return toDto(saved);
        }

        ProjectGroup group = projectGroupService.getActiveGroupWithInterns(dto.getGroupId());

        if (createdBy.getRole() != Role.ADMIN
                && (group.getSupervisor() == null || !Objects.equals(group.getSupervisor().getId(), createdById))) {
            throw new AccessDeniedException("Only an admin or the group supervisor can create a project in this group");
        }

        validateProjectScheduleAgainstCohort(dto.getStartDate(), dto.getEndDate(), group);

        List<Long> requestedIds = dto.getAssignedInternIds();
        if (requestedIds == null || requestedIds.isEmpty()) {
            if (group.getInterns() == null || group.getInterns().isEmpty()) {
                throw new BadRequestException(
                        "This cohort has no interns yet. Add at least one intern to the cohort before creating a project.");
            }
            requestedIds = group.getInterns().stream().map(User::getId).distinct().toList();
        } else {
            requestedIds = new ArrayList<>(new LinkedHashSet<>(requestedIds));
        }

        List<User> interns = userRepository.findAllById(requestedIds).stream()
                .filter(u -> u.getRole() == Role.INTERN)
                .toList();

        if (interns.size() != requestedIds.size()) {
            throw new BadRequestException("Every selected id must be a valid user with role INTERN");
        }
        for (User intern : interns) {
            long ongoing = projectRepository.countOngoingProjectsByInternId(intern.getId());
            if (ongoing >= MAX_SIMULTANEOUS_PROJECTS_PER_INTERN) {
                throw new BadRequestException(
                        "Intern " + intern.getId() + " already has " + ongoing
                                + " ongoing projects. Max allowed is " + MAX_SIMULTANEOUS_PROJECTS_PER_INTERN + ".");
            }
        }

        Set<Long> groupMemberIds = group.getInterns().stream().map(User::getId).collect(Collectors.toSet());
        for (Long id : requestedIds) {
            if (!groupMemberIds.contains(id)) {
                throw new BadRequestException("Every assigned intern must already belong to this cohort");
            }
        }

        if (group.getSupervisor() == null) {
            throw new BadRequestException("The cohort must have an assigned supervisor before projects can be created");
        }

        Project p = Project.builder()
                .title(dto.getTitle().trim())
                .description(dto.getDescription())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(dto.getStatus())
                .createdBy(createdBy)
                .group(group)
                .supervisor(group.getSupervisor())
                .assignedInterns(interns)
                .active(true)
                .deadlineWarningSent(false)
                .build();

        Project saved = projectRepository.save(p);
        sendProjectAssignedEmail(saved);
        return toDto(saved);
    }

    @Transactional
    public ProjectDto update(Long id, UpdateProjectRequestDto dto, Long editorId) {
        Project p = projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
        User editor = userRepository.findById(editorId).orElseThrow(() -> new ResourceNotFoundException("User", editorId));
        authorizeProjectEdit(p, editor);

        if (dto.getTitle() != null) {
            p.setTitle(dto.getTitle().trim());
        }
        if (dto.getDescription() != null) {
            p.setDescription(dto.getDescription());
        }
        if (dto.getStartDate() != null) {
            p.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            p.setEndDate(dto.getEndDate());
        }
        if (dto.getStatus() != null) {
            p.setStatus(dto.getStatus());
        }

        if (dto.getAssignedInternIds() != null) {
            if (p.getGroup() == null) {
                throw new BadRequestException("Project must belong to a group to assign interns");
            }
            ProjectGroup group = projectGroupService.getActiveGroupWithInterns(p.getGroup().getId());
            List<Long> updateIds = new ArrayList<>(new LinkedHashSet<>(dto.getAssignedInternIds()));
            List<User> interns = userRepository.findAllById(updateIds).stream()
                    .filter(u -> u.getRole() == Role.INTERN)
                    .toList();
            if (interns.size() != updateIds.size()) {
                throw new BadRequestException("All assigned users must have role INTERN");
            }
            Set<Long> groupMemberIds = group.getInterns().stream().map(User::getId).collect(Collectors.toSet());
            for (User u : interns) {
                if (!groupMemberIds.contains(u.getId())) {
                    throw new BadRequestException("All assigned interns must be members of the project group");
                }
            }
            p.setAssignedInterns(interns);
        }

        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            if (p.getGroup() != null) {
                ProjectGroup cohort = projectGroupService.getActiveGroupWithInterns(p.getGroup().getId());
                validateProjectScheduleAgainstCohort(p.getStartDate(), p.getEndDate(), cohort);
            } else {
                validateStandaloneProjectSchedule(p.getStartDate(), p.getEndDate());
            }
        }

        Project saved = projectRepository.save(p);
        return toDto(saved);
    }

    @Transactional
    public void softDelete(Long id, Long editorId) {
        Project p = projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project", id));
        User editor = userRepository.findById(editorId).orElseThrow(() -> new ResourceNotFoundException("User", editorId));
        authorizeProjectEdit(p, editor);
        p.setActive(false);
        projectRepository.save(p);
    }

    private void validateStandaloneProjectSchedule(LocalDate projectStart, LocalDate projectEnd) {
        LocalDate today = LocalDate.now();
        if (projectEnd == null) {
            throw new BadRequestException("Project end date is required.");
        }
        if (projectEnd.isBefore(today)) {
            throw new BadRequestException("Project end date cannot be in the past.");
        }
        if (projectStart != null && projectEnd.isBefore(projectStart)) {
            throw new BadRequestException("Project end date cannot be before the project start date.");
        }
    }

    /**
     * Project dates must stay within the cohort window when the cohort defines dates.
     * <ul>
     *     <li>Project start (required if cohort has both bounds) must lie between cohort start and cohort end (inclusive).</li>
     *     <li>Project end must not be after the cohort end date (inclusive bound).</li>
     *     <li>Project end must not be before project start when both are set.</li>
     * </ul>
     */
    private void validateProjectScheduleAgainstCohort(LocalDate projectStart, LocalDate projectEnd, ProjectGroup cohort) {
        LocalDate today = LocalDate.now();
        LocalDate cStart = cohort.getStartDate();
        LocalDate cEnd = cohort.getEndDate();

        if (projectEnd == null) {
            throw new BadRequestException("Project end date is required.");
        }
        if (projectEnd.isBefore(today)) {
            throw new BadRequestException("Project end date cannot be in the past.");
        }
        if (projectStart != null && projectEnd.isBefore(projectStart)) {
            throw new BadRequestException("Project end date cannot be before the project start date.");
        }

        if (cEnd != null) {
            if (projectEnd.isAfter(cEnd)) {
                throw new BadRequestException(
                        "Project end date cannot be after the cohort end date (" + cEnd + "). The project must finish while the cohort is still running.");
            }
            if (projectStart != null && projectStart.isAfter(cEnd)) {
                throw new BadRequestException(
                        "Project start date cannot be after the cohort end date (" + cEnd + ").");
            }
        }

        if (cStart != null && cEnd != null) {
            if (projectStart == null) {
                throw new BadRequestException(
                        "Project start date is required because this cohort has a defined period (" + cStart + " → " + cEnd + ").");
            }
            if (projectStart.isBefore(cStart)) {
                throw new BadRequestException(
                        "Project start date cannot be before the cohort start date (" + cStart + ").");
            }
            if (projectStart.isAfter(cEnd)) {
                throw new BadRequestException(
                        "Project start date cannot be after the cohort end date (" + cEnd + ").");
            }
        } else if (cStart != null) {
            if (projectStart != null && projectStart.isBefore(cStart)) {
                throw new BadRequestException(
                        "Project start date cannot be before the cohort start date (" + cStart + ").");
            }
        }
    }

    private void authorizeProjectEdit(Project p, User editor) {
        if (p.getGroup() != null && p.getGroup().getSupervisor() != null) {
            if (editor.getRole() != Role.ADMIN
                    && !Objects.equals(p.getGroup().getSupervisor().getId(), editor.getId())) {
                throw new AccessDeniedException("Only the group supervisor or an admin can change this project");
            }
            return;
        }
        if (editor.getRole() != Role.ADMIN && editor.getRole() != Role.SUPERVISOR) {
            throw new BadRequestException("Only Admin/Supervisor can update projects");
        }
    }

    private void sendProjectAssignedEmail(Project project) {
        if (project.getAssignedInterns() == null) {
            return;
        }
        String subject = "📁 New Project Assigned: " + project.getTitle();

        User supUser = project.getSupervisor() != null ? project.getSupervisor() : project.getCreatedBy();
        String supervisorName = supUser != null
                ? supUser.getFirstName() + " " + (supUser.getLastName() == null ? "" : supUser.getLastName())
                : "—";

        for (User intern : project.getAssignedInterns()) {
            if (intern.getEmail() == null) {
                continue;
            }
            Map<String, Object> vars = Map.of(
                    "firstName", intern.getFirstName(),
                    "header", "New Project Assigned",
                    "projectTitle", project.getTitle(),
                    "status", project.getStatus().name(),
                    "dueDate", project.getEndDate() != null ? project.getEndDate().toString() : "—",
                    "supervisor", supervisorName,
                    "message", "You have been assigned to a new project. Please review the details below.",
                    "ctaUrl", "http://localhost:3000/app/tasks"
            );
            emailService.sendProjectMailHtml(intern.getEmail(), subject, "emails/project-email.html", vars);
        }
    }

    private ProjectDto toDto(Project p) {
        User sup = p.getSupervisor();
        String supName = sup != null
                ? (sup.getFirstName() + " " + (sup.getLastName() == null ? "" : sup.getLastName())).trim()
                : null;
        return ProjectDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .status(p.getStatus())
                .groupId(p.getGroup() != null ? p.getGroup().getId() : null)
                .groupName(p.getGroup() != null ? p.getGroup().getName() : null)
                .supervisorId(sup != null ? sup.getId() : null)
                .supervisorName(supName)
                .createdBy(p.getCreatedBy() != null ? toUserSummary(p.getCreatedBy()) : null)
                .assignedInterns(p.getAssignedInterns() != null ? p.getAssignedInterns().stream().map(this::toUserSummary).toList() : List.of())
                .active(p.isActive())
                .createdAt(p.getCreatedAt())
                .build();
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
