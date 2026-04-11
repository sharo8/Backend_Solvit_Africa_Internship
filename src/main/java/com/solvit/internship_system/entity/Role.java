package com.solvit.internship_system.entity;

public enum Role {
    INTERN,
    SUPERVISOR,
    /** Human resources: read-heavy access to reports, interns, attendance; no full user administration. */
    HR,
    ADMIN
}
