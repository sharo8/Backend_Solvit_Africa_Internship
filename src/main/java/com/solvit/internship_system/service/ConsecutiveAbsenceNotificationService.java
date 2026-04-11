package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Attendance;
import com.solvit.internship_system.entity.ConsecutiveAbsenceWarning;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.repository.AttendanceRepository;
import com.solvit.internship_system.repository.ConsecutiveAbsenceWarningRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.PublicHolidayRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * When an intern reaches exactly five consecutive absent workdays (Mon–Fri within contract, excluding public holidays),
 * sends one warning email per streak end date (deduplicated in DB).
 */
@Service
@RequiredArgsConstructor
public class ConsecutiveAbsenceNotificationService {

    private static final int WARNING_THRESHOLD = 5;

    private final AttendanceRepository attendanceRepository;
    private final InternProfileRepository internProfileRepository;
    private final PublicHolidayRepository publicHolidayRepository;
    private final ConsecutiveAbsenceWarningRepository warningRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public void maybeNotifyOnAbsentStreak(Attendance saved) {
        if (saved == null || saved.getStatus() != Attendance.AttendanceStatus.ABSENT) {
            return;
        }
        Long userId = saved.getUser().getId();
        LocalDate end = saved.getAttendanceDate();
        InternProfile ip = internProfileRepository.findByUser_Id(userId).orElse(null);
        int streak = countConsecutiveAbsentWorkdaysEndingOn(userId, end, ip);
        if (streak != WARNING_THRESHOLD) {
            return;
        }
        if (warningRepository.existsByInternUserIdAndStreakEndDate(userId, end)) {
            return;
        }
        warningRepository.save(ConsecutiveAbsenceWarning.builder()
                .internUserId(userId)
                .streakEndDate(end)
                .createdAt(Instant.now())
                .build());
        User u = userRepository.findById(userId).orElse(null);
        if (u == null || u.getEmail() == null || u.getEmail().isBlank()) {
            return;
        }
        String fn = u.getFirstName() != null ? u.getFirstName() : "";
        String ln = u.getLastName() != null ? u.getLastName() : "";
        String display = (fn + " " + ln).trim();
        emailService.sendConsecutiveAbsenceProgramWarning(u.getEmail(), display, end);
    }

    private int countConsecutiveAbsentWorkdaysEndingOn(Long userId, LocalDate endDate, InternProfile ip) {
        int streak = 0;
        LocalDate d = endDate;
        for (int safety = 0; safety < 400; safety++) {
            if (!InternshipAttendanceRules.isWorkday(d)) {
                d = d.minusDays(1);
                continue;
            }
            if (ip == null || !InternshipAttendanceRules.isWithinContract(ip, d)) {
                break;
            }
            if (publicHolidayRepository.existsByHolidayDate(d)) {
                d = d.minusDays(1);
                continue;
            }
            var att = attendanceRepository.findByUser_IdAndAttendanceDate(userId, d);
            if (att.isEmpty() || att.get().getStatus() != Attendance.AttendanceStatus.ABSENT) {
                break;
            }
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }
}
