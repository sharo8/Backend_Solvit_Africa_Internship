package com.solvit.internship_system.dto.tasks;

public enum TaskAssignmentMode {
    /** Single intern (assigneeId required). */
    INDIVIDUAL,
    /** Same task created for every intern in the cohort (groupId required). */
    GROUP_COHORT
}
