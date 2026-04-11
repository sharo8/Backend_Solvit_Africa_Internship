package com.solvit.internship_system.scheduler;

import com.solvit.internship_system.entity.Evaluation;
import com.solvit.internship_system.entity.GroupStatus;
import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.repository.EvaluationRepository;
import com.solvit.internship_system.repository.ProjectGroupRepository;
import com.solvit.internship_system.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reminds supervisors to complete FINAL evaluations for interns in groups whose end date is approaching
 * and where no active FINAL evaluation exists yet for that intern/group pair.
 */
@Component
@RequiredArgsConstructor
public class EvaluationRemindersScheduler {

    private static final int END_DATE_WINDOW_DAYS = 14;

    private final ProjectGroupRepository projectGroupRepository;
    private final EvaluationRepository evaluationRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 8 * * MON", zone = "Africa/Kigali")
    @Transactional(readOnly = true)
    public void sendEvaluationReminders() {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(END_DATE_WINDOW_DAYS);
        List<ProjectGroup> groups = projectGroupRepository.findByActiveTrueAndStatusAndEndDateBetween(
                GroupStatus.ACTIVE, today, windowEnd);

        for (ProjectGroup g : groups) {
            ProjectGroup loaded = projectGroupRepository.findByIdAndActiveTrueWithInterns(g.getId()).orElse(null);
            if (loaded == null || loaded.getSupervisor() == null || loaded.getSupervisor().getEmail() == null) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (User intern : loaded.getInterns()) {
                boolean hasFinal = evaluationRepository.existsByIntern_IdAndTypeAndGroup_IdAndActiveTrue(
                        intern.getId(), Evaluation.EvaluationType.FINAL, loaded.getId());
                if (!hasFinal) {
                    String name = (intern.getFirstName() != null ? intern.getFirstName() : "")
                            + " "
                            + (intern.getLastName() != null ? intern.getLastName() : "");
                    missing.add(name.trim().isEmpty() ? "Intern #" + intern.getId() : name.trim());
                }
            }
            if (missing.isEmpty()) {
                continue;
            }

            String supFirst = loaded.getSupervisor().getFirstName() != null ? loaded.getSupervisor().getFirstName() : "there";
            String body = "Hello " + supFirst + ",\n\n"
                    + "Your project group \"" + loaded.getName() + "\" has an end date on or before "
                    + windowEnd + ". The following interns still need a FINAL evaluation recorded in the system:\n\n"
                    + missing.stream().map(m -> " - " + m).collect(Collectors.joining("\n"))
                    + "\n\nPlease create or submit evaluations in the app (Evaluations) before the group ends.\n\n"
                    + "— SOLVIT Africa Internship Monitoring";

            emailService.sendNotificationEmail(
                    loaded.getSupervisor().getEmail(),
                    "Reminder: FINAL evaluations for \"" + loaded.getName() + "\"",
                    body);
        }
    }
}
