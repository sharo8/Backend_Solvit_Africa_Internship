package com.solvit.internship_system.dto.attendance;

import com.solvit.internship_system.entity.Attendance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class BulkAttendanceRequestDto {
    @NotNull
    private LocalDate date;

    /** If null or empty, all active interns visible to the caller are included. */
    private List<Long> internIds;

    @NotNull
    private Attendance.AttendanceStatus status;

    private String notes;

    @NotBlank
    private String modificationReason;
}
