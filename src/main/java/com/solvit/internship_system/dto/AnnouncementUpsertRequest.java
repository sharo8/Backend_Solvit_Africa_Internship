package com.solvit.internship_system.dto;

import com.solvit.internship_system.entity.Role;
import lombok.Data;

@Data
public class AnnouncementUpsertRequest {
    private String title;
    private String content;
    /** Null means visible to all roles. */
    private Role targetRole;
    private Boolean pinned;
}
