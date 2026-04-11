package com.solvit.internship_system.entity;

/**
 * Self-service intern registration: {@link #PENDING} until HR or Admin approves.
 */
public enum HrApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
