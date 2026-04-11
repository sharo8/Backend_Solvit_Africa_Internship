package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.attendance.AdminAttendanceRowDto;
import com.solvit.internship_system.dto.attendance.AttendanceAnalyticsResponseDto;
import com.solvit.internship_system.dto.attendance.AttendanceListResponseDto;
import com.solvit.internship_system.dto.attendance.AttendanceStatsDto;
import com.solvit.internship_system.dto.attendance.BulkAttendanceRequestDto;
import com.solvit.internship_system.dto.attendance.CreatePublicHolidayRequestDto;
import com.solvit.internship_system.dto.attendance.PublicHolidayDto;
import com.solvit.internship_system.dto.attendance.UpsertAttendanceRequestDto;
import com.solvit.internship_system.entity.Attendance;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.AdminAttendanceService;
import com.solvit.internship_system.service.AttendanceCalculationService;
import com.solvit.internship_system.service.PublicHolidayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR')")
public class AdminAttendanceController {

    private final AdminAttendanceService adminAttendanceService;
    private final PublicHolidayService publicHolidayService;
    private final JwtUtil jwtUtil;

    /**
     * GET list: returns combined records + stats + pagination when {@code combined=true} (default for new clients).
     */
    @GetMapping
    public ResponseEntity<?> getForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(required = false) Attendance.AttendanceStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") boolean combined,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        if (combined) {
            AttendanceListResponseDto body = adminAttendanceService.getList(date, supervisorId, status, search, page, limit, role, userId);
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok(adminAttendanceService.getForDate(date, supervisorId, status));
    }

    @GetMapping("/public-holidays")
    public ResponseEntity<List<PublicHolidayDto>> listPublicHolidays(
            @RequestParam(required = false) Integer year
    ) {
        int y = year != null ? year : LocalDate.now(AttendanceCalculationService.APP_ZONE).getYear();
        return ResponseEntity.ok(publicHolidayService.listForYear(y));
    }

    @PostMapping("/public-holidays")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<PublicHolidayDto> createPublicHoliday(@Valid @RequestBody CreatePublicHolidayRequestDto body) {
        return ResponseEntity.ok(publicHolidayService.create(body));
    }

    @DeleteMapping("/public-holidays/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> deletePublicHoliday(@PathVariable Long id) {
        publicHolidayService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<AttendanceStatsDto> stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long supervisorId,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        return ResponseEntity.ok(adminAttendanceService.stats(date, supervisorId, role, userId));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AttendanceAnalyticsResponseDto> analytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(required = false, defaultValue = "true") boolean includeCompleted,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        if (days > 370) {
            throw new BadRequestException("Range too large (max 370 days)");
        }
        return ResponseEntity.ok(adminAttendanceService.getAnalytics(from, to, supervisorId, includeCompleted, role, userId));
    }

    @GetMapping("/range")
    public ResponseEntity<List<AdminAttendanceRowDto>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supervisorId,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        return ResponseEntity.ok(adminAttendanceService.getForDateRange(from, to, supervisorId, role, userId));
    }

    @GetMapping("/intern/{internId}")
    public ResponseEntity<List<AdminAttendanceRowDto>> internHistory(
            @PathVariable Long internId,
            @RequestParam int month,
            @RequestParam int year,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        return ResponseEntity.ok(adminAttendanceService.internMonthlyHistory(internId, month, year, role, userId));
    }

    /** Create or update (upsert) by intern + date. */
    @PostMapping
    public ResponseEntity<AdminAttendanceRowDto> upsert(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UpsertAttendanceRequestDto dto
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        return ResponseEntity.ok(adminAttendanceService.upsert(dto, userId, role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminAttendanceRowDto> updatePut(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody UpsertAttendanceRequestDto dto
    ) {
        return updateInternal(authHeader, id, dto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminAttendanceRowDto> updatePatch(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @Valid @RequestBody UpsertAttendanceRequestDto dto
    ) {
        return updateInternal(authHeader, id, dto);
    }

    private ResponseEntity<AdminAttendanceRowDto> updateInternal(String authHeader, Long id, UpsertAttendanceRequestDto dto) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        return ResponseEntity.ok(adminAttendanceService.update(id, dto, userId, role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminAttendanceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<AdminAttendanceRowDto>> bulk(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody BulkAttendanceRequestDto dto
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        return ResponseEntity.ok(adminAttendanceService.bulk(dto, userId, role));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(required = false) Attendance.AttendanceStatus status,
            @RequestParam(required = false, defaultValue = "csv") String format,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        Role role = resolveRole(authHeader);
        if ("pdf".equalsIgnoreCase(format)) {
            byte[] bytes = adminAttendanceService.exportPdf(from, to, supervisorId, status, role, userId);
            String filename = "attendance_" + from + "_to_" + to + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        }
        byte[] bytes = adminAttendanceService.exportCsv(from, to, supervisorId, status, role, userId);
        String filename = "attendance_" + from + "_to_" + to + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv"))
                .body(bytes);
    }

    private Role resolveRole(String authHeader) {
        String token = authHeader.substring(7);
        String r = jwtUtil.getRoleFromToken(token);
        if (r == null) {
            throw new AccessDeniedException("Missing role");
        }
        if ("ADMIN".equalsIgnoreCase(r)) {
            return Role.ADMIN;
        }
        if ("HR".equalsIgnoreCase(r)) {
            return Role.HR;
        }
        if ("SUPERVISOR".equalsIgnoreCase(r)) {
            return Role.SUPERVISOR;
        }
        throw new AccessDeniedException("Attendance API requires ADMIN, HR, or SUPERVISOR");
    }
}
