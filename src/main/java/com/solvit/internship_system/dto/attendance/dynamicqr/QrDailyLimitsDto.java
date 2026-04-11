package com.solvit.internship_system.dto.attendance.dynamicqr;

public record QrDailyLimitsDto(
        int downloadsUsed,
        int downloadsLimit,
        int copiesUsed,
        int copiesLimit,
        int downloadsRemaining,
        int copiesRemaining,
        /** New JWT QR generations already used today. */
        int generationsUsed,
        /** Effective max generations today (base 2 + supervisor-approved bonus). */
        int generationsLimit,
        int generationsRemaining,
        /** Sum of extra slots granted by supervisor approvals today (each grant is 1 or 2). */
        int bonusGenerationsTotal,
        int baseGenerationLimit,
        int effectiveGenerationLimit,
        /**
         * NONE — no pending/rejected state to show; PENDING — waiting for supervisor; REJECTED — last decision today was reject
         * (intern may send a new request after exhausting quota again).
         */
        String extraAccessRequestStatus,
        boolean canRequestExtraAccess,
        boolean supervisorAssigned
) {}
