package com.solvit.internship_system.report.model;

/**
 * How numeric values are labeled next to horizontal bar charts.
 */
public enum HorizontalBarValueMode {
    /** Values are rates; display with a percent sign (e.g. 72.5%). */
    PERCENT,
    /** Dimensionless 0–100 style scores (e.g. AI composite); no percent sign. */
    SCORE,
    /** Counts or other whole numbers; no percent sign. */
    COUNT
}
