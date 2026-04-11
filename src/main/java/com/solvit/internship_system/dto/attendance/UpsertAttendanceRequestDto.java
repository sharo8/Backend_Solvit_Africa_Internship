package com.solvit.internship_system.dto.attendance;

import com.solvit.internship_system.entity.Attendance;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class UpsertAttendanceRequestDto {
    @NotNull
    private Long internId;

    @NotNull
    private LocalDate date;

    private LocalTime checkInTime;
    private LocalTime checkOutTime;

    /** Optional; when absent, final status is derived from times and excused flag. */
    private Attendance.AttendanceStatus status;

    /** When true, final status is EXCUSED (admin-validated excuse). */
    private Boolean excused;

    private String excuseReason;

    private String notes;

    private Boolean manualEntry;

    /** Required for admin updates; stored in notes with actor and timestamp. */
    private String modificationReason;
}

