package com.solvit.internship_system.report;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

public final class ReportDateResolver {

    private ReportDateResolver() {}

    public static ReportDateRange resolve(ReportRequest req) {
        LocalDate today = LocalDate.now();
        String p = req.getPeriod() != null ? req.getPeriod().trim().toUpperCase() : "";

        if (req.getDateFrom() != null && req.getDateTo() != null) {
            return new ReportDateRange(req.getDateFrom(), req.getDateTo(),
                    req.getDateFrom() + " — " + req.getDateTo());
        }

        if ("CUSTOM".equals(p) && req.getDateFrom() != null && req.getDateTo() != null) {
            return new ReportDateRange(req.getDateFrom(), req.getDateTo(),
                    req.getDateFrom() + " — " + req.getDateTo());
        }

        if ("DAILY".equals(p) && req.getDateFrom() != null) {
            LocalDate d = req.getDateFrom();
            return new ReportDateRange(d, d, d.toString());
        }

        Integer y = req.getYear() != null ? req.getYear() : today.getYear();
        WeekFields iso = WeekFields.ISO;

        if ("WEEKLY".equals(p) || req.getWeek() != null) {
            int wYear = req.getYear() != null ? req.getYear() : today.get(iso.weekBasedYear());
            int week = req.getWeek() != null ? req.getWeek() : today.get(iso.weekOfWeekBasedYear());
            LocalDate start = LocalDate.of(wYear, 1, 4)
                    .with(iso.weekOfWeekBasedYear(), week)
                    .with(iso.dayOfWeek(), 1);
            LocalDate end = start.plusDays(6);
            return new ReportDateRange(start, end, "Week " + week + " " + wYear + " (" + start + " — " + end + ")");
        }

        if ("MONTHLY".equals(p) || (req.getMonth() != null && req.getYear() != null)) {
            int month = req.getMonth() != null ? req.getMonth() : today.getMonthValue();
            LocalDate start = LocalDate.of(y, month, 1);
            LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
            return new ReportDateRange(start, end,
                    Month.of(month).name() + " " + y);
        }

        if ("QUARTERLY".equals(p) || req.getQuarter() != null) {
            int q = req.getQuarter() != null ? req.getQuarter() : ((today.getMonthValue() - 1) / 3 + 1);
            int startMonth = (q - 1) * 3 + 1;
            LocalDate start = LocalDate.of(y, startMonth, 1);
            LocalDate end = start.plusMonths(3).minusDays(1);
            return new ReportDateRange(start, end, "Q" + q + " " + y);
        }

        if ("ANNUAL".equals(p)) {
            LocalDate start = LocalDate.of(y, 1, 1);
            LocalDate end = LocalDate.of(y, 12, 31);
            return new ReportDateRange(start, end, "Year " + y);
        }

        // Default: rolling 30 days
        LocalDate end = today;
        LocalDate start = today.minusDays(29);
        return new ReportDateRange(start, end, "Last 30 days (" + start + " — " + end + ")");
    }

    public static ReportDateRange weekContaining(LocalDate anyDay) {
        WeekFields iso = WeekFields.ISO;
        LocalDate start = anyDay.with(iso.dayOfWeek(), 1);
        LocalDate end = start.plusDays(6);
        int w = anyDay.get(iso.weekOfWeekBasedYear());
        return new ReportDateRange(start, end, "Week " + w + " (" + start + " — " + end + ")");
    }

    public static ReportDateRange monthOf(LocalDate day) {
        LocalDate start = day.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = day.with(TemporalAdjusters.lastDayOfMonth());
        return new ReportDateRange(start, end, day.getMonth().name() + " " + day.getYear());
    }
}
