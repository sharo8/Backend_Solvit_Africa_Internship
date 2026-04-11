package com.solvit.internship_system.scheduler;

import com.solvit.internship_system.entity.Task;
import com.solvit.internship_system.repository.TaskRepository;
import com.solvit.internship_system.service.NotificationService;
import com.solvit.internship_system.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskAlertScheduler {

    private static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");

    private final TaskRepository taskRepo;
    private final TaskService taskService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Kigali")
    @Transactional
    public void markOverdueAndNotify() {
        LocalDate today = LocalDate.now(KIGALI);
        List<Task> overdueTasks = taskRepo.findByActiveTrueAndDueDateBeforeAndStatusNotIn(
                today,
                Set.of(Task.TaskStatus.VALIDATED, Task.TaskStatus.CANCELLED, Task.TaskStatus.OVERDUE)
        );

        int count = 0;
        for (Task task : overdueTasks) {
            task.setStatus(Task.TaskStatus.OVERDUE);
            if (!task.isOverdueAlertSent()) {
                taskService.sendOverdueAlert(task);
                task.setOverdueAlertSent(true);
            }
            count++;
        }
        taskRepo.saveAll(overdueTasks);
        log.info("[TaskAlertScheduler] Marked {} tasks as OVERDUE", count);
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Kigali")
    @Transactional
    public void sendDeadlineReminders() {
        LocalDate tomorrow = LocalDate.now(KIGALI).plusDays(1);
        List<Task> dueTomorrow = taskRepo.findByActiveTrueAndStatusInAndDueDateAndDueTomorrowReminderSentFalse(
                Set.of(
                        Task.TaskStatus.PENDING,
                        Task.TaskStatus.IN_PROGRESS,
                        Task.TaskStatus.IN_REVIEW,
                        Task.TaskStatus.REJECTED,
                        Task.TaskStatus.OVERDUE
                ),
                tomorrow
        );

        for (Task task : dueTomorrow) {
            taskService.sendDueTomorrowReminder(task);
            task.setDueTomorrowReminderSent(true);
        }
        taskRepo.saveAll(dueTomorrow);
        log.info("[TaskAlertScheduler] Sent deadline reminders for {} tasks", dueTomorrow.size());
    }

    @Scheduled(cron = "0 30 8 * * MON", zone = "Africa/Kigali")
    public void sendWeeklySummaryToSupervisors() {
        notificationService.sendWeeklyTaskSummaryToAllSupervisors();
        log.info("[TaskAlertScheduler] Weekly task summary sent to all supervisors");
    }
}
