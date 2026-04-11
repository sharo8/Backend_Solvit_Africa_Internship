package com.solvit.internship_system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsDTO {

    private long total;
    private long active;
    private long inactive;
    private Map<String, Long> byRole;
    private long newThisMonth;
    private long verifiedEmails;
}
