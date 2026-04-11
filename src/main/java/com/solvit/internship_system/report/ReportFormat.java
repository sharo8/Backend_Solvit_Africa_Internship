package com.solvit.internship_system.report;

import com.solvit.internship_system.exception.BadRequestException;

public enum ReportFormat {
    PDF,
    CSV,
    EXCEL;

    public static ReportFormat fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            return CSV;
        }
        try {
            return ReportFormat.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown format: " + raw);
        }
    }
}
