package com.solvit.internship_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SidebarBadgesDto {
    private int pendingLeave;
    private int unreadMessages;
    private int unreadNotifications;
}
