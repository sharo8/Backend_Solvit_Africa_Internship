package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Project;
import com.solvit.internship_system.entity.Task;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.repository.ProjectRepository;
import com.solvit.internship_system.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TasksRemindersScheduler {

    private static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TaskService taskService;
    private final EmailService emailService;

    /** Noon (Kigali): tasks due today, still TODO / 0% progress — nudge intern and supervisor. */
    @Scheduled(cron = "0 0 12 * * *", zone = "Africa/Kigali")
    public void sendSameDayIncompleteReminders() {
        LocalDate today = LocalDate.now(KIGALI);
        List<Task> tasks = taskRepository.findDueTodayPendingNoProgressReminderNotSent(today, Task.TaskStatus.PENDING);
        for (Task t : tasks) {
            taskService.sendSameDayIncompleteReminder(t);
        }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Kigali")
    public void sendProjectDeadlineWarnings() {
        LocalDate target = LocalDate.now(KIGALI).plusDays(3);
        List<Project> projects = projectRepository.findByActiveTrueAndEndDateAndStatusAndDeadlineWarningSentFalse(
                target,
                Project.ProjectStatus.ACTIVE
        );
        for (Project p : projects) {
            String subject = "⚠️ Project '" + p.getTitle() + "' due in 3 days";
            User sup = p.getSupervisor() != null ? p.getSupervisor() : p.getCreatedBy();
            String supervisorName = sup != null
                    ? sup.getFirstName() + " " + (sup.getLastName() == null ? "" : sup.getLastName())
                    : "—";

            if (sup != null && sup.getEmail() != null) {
                Map<String, Object> vars = Map.of(
                        "firstName", sup.getFirstName(),
                        "header", "Project Deadline Warning",
                        "projectTitle", p.getTitle(),
                        "status", p.getStatus().name(),
                        "dueDate", p.getEndDate() != null ? p.getEndDate().toString() : "—",
                        "supervisor", supervisorName,
                        "message", "This is a reminder that the project deadline is coming soon. Please review details below.",
                        "ctaUrl", "http://localhost:3000/app/tasks"
                );
                emailService.sendProjectMailHtml(sup.getEmail(), subject, "emails/project-email.html", vars);
            }

            for (User intern : p.getAssignedInterns()) {
                if (intern.getEmail() == null) continue;
                Map<String, Object> vars = Map.of(
                        "firstName", intern.getFirstName(),
                        "header", "Project Deadline Warning",
                        "projectTitle", p.getTitle(),
                        "status", p.getStatus().name(),
                        "dueDate", p.getEndDate() != null ? p.getEndDate().toString() : "—",
                        "supervisor", supervisorName,
                        "message", "This is a reminder that the project deadline is coming soon. Please review details below.",
                        "ctaUrl", "http://localhost:3000/app/tasks"
                );
                emailService.sendProjectMailHtml(intern.getEmail(), subject, "emails/project-email.html", vars);
            }

            p.setDeadlineWarningSent(true);
            projectRepository.save(p);
        }
    }
}
