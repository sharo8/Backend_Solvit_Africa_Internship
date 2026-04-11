package com.solvit.internship_system.dto.attendance;

import com.solvit.internship_system.entity.Attendance;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class AdminAttendanceRowDto {
    private Long id;
    private Long internId;
    private String firstName;
    private String lastName;
    private String profilePhotoUrl;
    private String universityId;
    private Long supervisorId;
    private String supervisorName;
    private LocalDate date;
    private Instant checkInAt;
    private Instant checkOutAt;
    private Attendance.AttendanceStatus status;
    private Integer durationMinutes;
    private boolean excused;
    private String excuseReason;
    private String notes;
    private boolean manualEntry;
    private Long modifiedByUserId;
    private String modifiedByName;
}

