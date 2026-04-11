package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.attendance.dynamicqr.QrTokenClaims;
import com.solvit.internship_system.entity.Attendance;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.AttendanceRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.PublicHolidayRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final PublicHolidayRepository publicHolidayRepository;
    private final NotificationService notificationService;
    private final QrAttendanceService qrAttendanceService;
    private final QrTokenService qrTokenService;

    private static boolean looksLikeJwt(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return token.chars().filter(ch -> ch == '.').count() == 2L;
    }

    private static String appendQrNote(String existing, String tag) {
        if (existing == null || existing.isBlank()) {
            return tag;
        }
        if (existing.contains(tag)) {
            return existing;
        }
        return existing + " | " + tag;
    }

    @Transactional
    public Attendance checkIn(Long userId, String latitude, String longitude, String location) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        LocalDate today = AttendanceCalculationService.todayKigali();
        if (user.getRole() == Role.INTERN) {
            InternProfile ip = internProfileRepository.findByUser_Id(userId).orElse(null);
            if (ip == null || !InternshipAttendanceRules.eligibleForAttendanceOnDate(ip, today)) {
                throw new com.solvit.internship_system.exception.BadRequestException(
                        "Attendance is only available on weekdays during your active internship period.");
            }
            if (publicHolidayRepository.existsByHolidayDate(today)) {
                throw new com.solvit.internship_system.exception.BadRequestException(
                        "Check-in is not available on public holidays.");
            }
        }
        if (attendanceRepository.findByUser_IdAndAttendanceDate(userId, today).isPresent()) {
            throw new com.solvit.internship_system.exception.BadRequestException("Already checked in today.");
        }
        Instant now = Instant.now();
        Attendance a = Attendance.builder()
                .user(user)
                .attendanceDate(today)
                .checkInAt(now)
                .latitude(latitude)
                .longitude(longitude)
                .checkInLocation(location)
                .build();
        applyDerivedStatus(a);
        return attendanceRepository.save(a);
    }

    /**
     * Check-in / check-out using either the daily office HMAC token (building screen) or the intern's
     * rotating JWT from {@code /api/attendance/qr/my-qr-token} (paste / same device).
     */
    @Transactional
    public Attendance checkInWithQr(Long userId, String qrToken, String latitude, String longitude, String location) {
        LocalDate today = AttendanceCalculationService.todayKigali();
        if (qrAttendanceService.isValidToken(today, qrToken)) {
            Attendance a = checkIn(userId, latitude, longitude, location);
            a.setNotes("QR_CHECKIN");
            return attendanceRepository.save(a);
        }
        if (looksLikeJwt(qrToken)) {
            QrTokenClaims claims = qrTokenService.validateAndConsume(qrToken);
            if (!claims.internId().equals(userId)) {
                throw new com.solvit.internship_system.exception.BadRequestException(
                        "This QR code belongs to another account.");
            }
            User u = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
            if (u.getRole() != Role.INTERN) {
                throw new com.solvit.internship_system.exception.BadRequestException(
                        "Only interns can use a personal attendance QR code here.");
            }
            Optional<Attendance> opt = attendanceRepository.findByUser_IdAndAttendanceDate(userId, today);
            if (opt.isPresent()) {
                Attendance existing = opt.get();
                if (existing.getCheckOutAt() != null) {
                    throw new com.solvit.internship_system.exception.BadRequestException(
                            "You have already completed attendance for today.");
                }
                Attendance out = checkOut(userId, latitude, longitude, location);
                out.setNotes(appendQrNote(out.getNotes(), "INTERN_DYNAMIC_QR_SELF"));
                return attendanceRepository.save(out);
            }
            Attendance in = checkIn(userId, latitude, longitude, location);
            in.setNotes(appendQrNote(in.getNotes(), "INTERN_DYNAMIC_QR_SELF"));
            return attendanceRepository.save(in);
        }
        throw new com.solvit.internship_system.exception.BadRequestException("Invalid or expired QR code.");
    }

    @Transactional
    public Attendance checkOut(Long userId, String latitude, String longitude, String location) {
        LocalDate today = AttendanceCalculationService.todayKigali();
        Attendance a = attendanceRepository.findByUser_IdAndAttendanceDate(userId, today)
                .orElseThrow(() -> new com.solvit.internship_system.exception.BadRequestException("No check-in found for today."));
        a.setCheckOutAt(Instant.now());
        a.setCheckOutLocation(location);
        if (latitude != null) {
            a.setLatitude(latitude);
        }
        if (longitude != null) {
            a.setLongitude(longitude);
        }
        applyDerivedStatus(a);
        return attendanceRepository.save(a);
    }

    private void applyDerivedStatus(Attendance a) {
        Attendance.AttendanceStatus st = AttendanceCalculationService.calculateStatus(
                a.getCheckInAt(), a.getCheckOutAt(), a.isExcused());
        a.setStatus(st);
        a.setDurationMinutes(AttendanceCalculationService.calcDurationMinutes(a.getCheckInAt(), a.getCheckOutAt()));
    }

    public Attendance getByUserAndDate(Long userId, LocalDate date) {
        return attendanceRepository.findByUser_IdAndAttendanceDate(userId, date)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found"));
    }

    /** No row for that day → {@code null} (used by APIs that must not return 404). */
    public Attendance getByUserAndDateOrNull(Long userId, LocalDate date) {
        return attendanceRepository.findByUser_IdAndAttendanceDate(userId, date).orElse(null);
    }

    public List<Attendance> getByUserInRange(Long userId, LocalDate start, LocalDate end) {
        return attendanceRepository.findForUserInDateRange(userId, start, end);
    }

    @Transactional
    public Attendance validate(Long attendanceId, Long supervisorId) {
        Attendance a = attendanceRepository.findById(attendanceId).orElseThrow(() -> new ResourceNotFoundException("Attendance", attendanceId));
        a.setValidatedBy(supervisorId);
        a.setValidatedAt(Instant.now());
        a.setStatus(Attendance.AttendanceStatus.VALIDATED);
        a = attendanceRepository.save(a);
        notificationService.create(a.getUser().getId(), "Attendance Validated",
                "Your attendance has been validated by your supervisor.", com.solvit.internship_system.entity.Notification.NotificationType.ATTENDANCE_REMINDER,
                "Attendance", a.getId(), true);
        return a;
    }

    /**
     * Supervisor/admin/HR scans the intern's rotating JWT QR. Reuses {@link #checkIn}/{@link #checkOut}
     * and eligibility rules; does not use the daily office HMAC token ({@link #checkInWithQr}).
     */
    @Transactional
    public Attendance applySupervisorScannedInternQr(Long internId, Long scannedByUserId, String lat, String lon, String locationLabel) {
        User scanner = userRepository.findById(scannedByUserId).orElseThrow(() -> new ResourceNotFoundException("User", scannedByUserId));
        LocalDate today = AttendanceCalculationService.todayKigali();
        Optional<Attendance> opt = attendanceRepository.findByUser_IdAndAttendanceDate(internId, today);
        if (opt.isPresent()) {
            Attendance existing = opt.get();
            if (existing.getCheckOutAt() != null) {
                throw new com.solvit.internship_system.exception.BadRequestException(
                        "This intern has already completed attendance for today.");
            }
            Attendance out = checkOut(internId, lat, lon, locationLabel);
            out.setModifiedBy(scanner);
            return attendanceRepository.save(out);
        }
        Attendance in = checkIn(internId, lat, lon, locationLabel);
        in.setModifiedBy(scanner);
        String n = in.getNotes();
        if (n == null || n.isBlank()) {
            in.setNotes("INTERN_DYNAMIC_QR");
        } else if (!n.contains("INTERN_DYNAMIC_QR")) {
            in.setNotes(n + " | INTERN_DYNAMIC_QR");
        }
        return attendanceRepository.save(in);
    }
}
