package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.Attendance;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Business rules for attendance (Africa/Kigali). Spec: calculateStatus priority order.
 */
public final class AttendanceCalculationService {

    public static final ZoneId APP_ZONE = ZoneId.of("Africa/Kigali");

    /** Check-in strictly after this local time (Africa/Kigali) is classified as LATE when not excused. */
    private static final LocalTime LATE_AFTER = LocalTime.of(9, 30);

    /**
     * After this local time on a workday, interns with no attendance row are auto-marked ABSENT
     * (scheduler + on-demand derivation in {@code AdminAttendanceService}).
     */
    private static final LocalTime AUTO_ABSENT_AFTER = LocalTime.of(18, 30);

    private static final LocalTime HALF_DAY_CHECKOUT_BEFORE = LocalTime.of(13, 0);
    private static final int HALF_DAY_MAX_MINUTES = 240;

    private AttendanceCalculationService() {
    }

    /**
     * Duration in minutes when both instants exist; null if still on site or invalid.
     */
    public static Integer calcDurationMinutes(Instant checkIn, Instant checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return null;
        }
        return (int) Duration.between(checkIn, checkOut).toMinutes();
    }

    /**
     * Applies spec order: EXCUSED → ABSENT → LATE → HALF_DAY (checkout/duration) → PRESENT.
     */
    public static Attendance.AttendanceStatus calculateStatus(
            Instant checkIn,
            Instant checkOut,
            boolean isExcused
    ) {
        if (isExcused) {
            return Attendance.AttendanceStatus.EXCUSED;
        }
        if (checkIn == null) {
            return Attendance.AttendanceStatus.ABSENT;
        }

        LocalTime inLocal = LocalDateTime.ofInstant(checkIn, APP_ZONE).toLocalTime();
        if (inLocal.isAfter(LATE_AFTER)) {
            return Attendance.AttendanceStatus.LATE;
        }

        if (checkOut != null) {
            LocalTime outLocal = LocalDateTime.ofInstant(checkOut, APP_ZONE).toLocalTime();
            if (outLocal.isBefore(HALF_DAY_CHECKOUT_BEFORE)) {
                return Attendance.AttendanceStatus.HALF_DAY;
            }
        }

        Integer minutes = calcDurationMinutes(checkIn, checkOut);
        if (minutes != null && minutes < HALF_DAY_MAX_MINUTES) {
            return Attendance.AttendanceStatus.HALF_DAY;
        }

        return Attendance.AttendanceStatus.PRESENT;
    }

    public static LocalDate todayKigali() {
        return LocalDate.now(APP_ZONE);
    }

    /** TTL from now until next midnight in {@link #APP_ZONE} (for daily rate limits). */
    public static Duration ttlUntilMidnightKigali() {
        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(APP_ZONE);
        return Duration.between(now, nextMidnight);
    }

    public static LocalTime autoAbsentCutoffLocal() {
        return AUTO_ABSENT_AFTER;
    }
}
