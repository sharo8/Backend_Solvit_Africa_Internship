package com.solvit.internship_system.report;

import com.solvit.internship_system.exception.BadRequestException;

public enum ReportType {
    ATTENDANCE_SUMMARY,
    INTERN_INDIVIDUAL,
    SUPERVISOR,
    GROUP,
    PROJECT,
    TASK,
    EVALUATION,
    FEEDBACK,
    LEARNING_PATH,
    AI_PERFORMANCE,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    ANNUAL,
    GROUP_COMPARISON;

    public static ReportType fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("reportType is required");
        }
        String u = raw.trim().toUpperCase();
        try {
            return ReportType.valueOf(u);
        } catch (IllegalArgumentException e) {
            ReportType legacy = legacyMap(u);
            if (legacy != null) {
                return legacy;
            }
            throw new BadRequestException("Unknown report type: " + raw);
        }
    }

    /** Map old dashboard report keys to new enum. */
    private static ReportType legacyMap(String u) {
        return switch (u) {
            case "ATTENDANCE" -> ATTENDANCE_SUMMARY;
            case "PERFORMANCE", "COHORT", "AT_RISK", "FULL" -> AI_PERFORMANCE;
            case "TASKS" -> TASK;
            case "FEEDBACK" -> FEEDBACK;
            default -> null;
        };
    }
}
