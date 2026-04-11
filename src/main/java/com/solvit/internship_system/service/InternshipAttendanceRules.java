package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.InternProfile;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Attendance is expected on weekdays (Mon–Fri) only, between internship start and end (inclusive).
 */
public final class InternshipAttendanceRules {

    private InternshipAttendanceRules() {
    }

    public static boolean isWorkday(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    /**
     * True when both dates are set and {@code date} falls within [start, end] inclusive.
     */
    public static boolean isWithinContract(InternProfile profile, LocalDate date) {
        if (profile == null || date == null) {
            return false;
        }
        LocalDate start = profile.getInternshipStartDate();
        LocalDate end = profile.getInternshipEndDate();
        if (start == null || end == null) {
            return false;
        }
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * Intern must check in only on workdays during an active contract window.
     */
    public static boolean eligibleForAttendanceOnDate(InternProfile profile, LocalDate date) {
        return isWorkday(date) && isWithinContract(profile, date);
    }

    /**
     * Expected workdays in a calendar month overlapping the internship contract.
     */
    /**
     * ACTIVE = within [start,end], UPCOMING = before start, COMPLETED = after end, NO_DATES = missing bounds.
     */
    public static String computeInternshipStatus(InternProfile profile, LocalDate today) {
        if (profile == null) {
            return "NO_DATES";
        }
        LocalDate start = profile.getInternshipStartDate();
        LocalDate end = profile.getInternshipEndDate();
        if (start == null || end == null) {
            return "NO_DATES";
        }
        if (today.isBefore(start)) {
            return "UPCOMING";
        }
        if (today.isAfter(end)) {
            return "COMPLETED";
        }
        return "ACTIVE";
    }

    public static long countExpectedWorkdaysInMonth(InternProfile ip, YearMonth ym) {
        if (ip == null || ym == null) {
            return 0;
        }
        LocalDate contractStart = ip.getInternshipStartDate();
        LocalDate contractEnd = ip.getInternshipEndDate();
        if (contractStart == null || contractEnd == null) {
            return 0;
        }
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        LocalDate from = monthStart.isBefore(contractStart) ? contractStart : monthStart;
        LocalDate to = monthEnd.isAfter(contractEnd) ? contractEnd : monthEnd;
        if (to.isBefore(from)) {
            return 0;
        }
        long count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkday(d)) {
                count++;
            }
        }
        return count;
    }
}
