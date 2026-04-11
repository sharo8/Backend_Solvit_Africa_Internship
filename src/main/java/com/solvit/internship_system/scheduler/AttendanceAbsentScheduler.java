package com.solvit.internship_system.scheduler;

import com.solvit.internship_system.service.AdminAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Auto-mark ABSENT at 18:30 Africa/Kigali when no attendance exists for eligible interns (cron in Kigali timezone).
 */
@Component
@RequiredArgsConstructor
public class AttendanceAbsentScheduler {

    private final AdminAttendanceService adminAttendanceService;

    @Scheduled(cron = "0 30 18 * * ?", zone = "Africa/Kigali")
    public void markAbsentAfterCutoff() {
        adminAttendanceService.runEndOfDayAutoMark();
    }
}
