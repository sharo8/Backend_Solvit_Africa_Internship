package com.solvit.internship_system.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceListResponseDto {
    private List<AdminAttendanceRowDto> records;
    private AttendanceStatsDto stats;
    private PaginationDto pagination;
}
