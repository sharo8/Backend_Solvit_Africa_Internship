package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Notifies all ADMIN and HR users 30 days and 7 days before an intern's scheduled end date.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternshipReminderScheduler {

    private final InternProfileRepository internProfileRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 15 8 * * *", zone = "Africa/Kigali")
    @Transactional
    public void sendInternshipEndReminders() {
        LocalDate today = AttendanceCalculationService.todayKigali();
        LocalDate endIn30 = today.plusDays(30);
        LocalDate endIn7 = today.plusDays(7);

        processWindow(endIn30, true);
        processWindow(endIn7, false);
    }

    private void processWindow(LocalDate targetEndDate, boolean thirtyDayWindow) {
        List<InternProfile> profiles = internProfileRepository.findByInternshipEndDate(targetEndDate);
        List<User> hrAdmins = userRepository.findByRoleInAndActiveTrue(List.of(Role.ADMIN, Role.HR));
        if (hrAdmins.isEmpty()) {
            log.warn("No active ADMIN/HR users to notify for internship reminders");
        }

        for (InternProfile ip : profiles) {
            User intern = ip.getUser();
            if (intern == null || !intern.isActive()) {
                continue;
            }
            if (thirtyDayWindow) {
                if (Objects.equals(ip.getReminder30dSentForEndDate(), ip.getInternshipEndDate())) {
                    continue;
                }
                ip.setReminder30dSentForEndDate(ip.getInternshipEndDate());
            } else {
                if (Objects.equals(ip.getReminder7dSentForEndDate(), ip.getInternshipEndDate())) {
                    continue;
                }
                ip.setReminder7dSentForEndDate(ip.getInternshipEndDate());
            }
            internProfileRepository.save(ip);

            String name = (intern.getFirstName() != null ? intern.getFirstName() : "")
                    + " "
                    + (intern.getLastName() != null ? intern.getLastName() : "");
            name = name.trim();
            String window = thirtyDayWindow
                    ? "30 days before scheduled end (one-month notice)"
                    : "7 days before scheduled end";

            for (User recipient : hrAdmins) {
                if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                    continue;
                }
                emailService.sendInternshipEndingReminderEmail(
                        recipient.getEmail(),
                        name.isEmpty() ? "Intern #" + intern.getId() : name,
                        targetEndDate,
                        window
                );
            }
        }
    }
}
