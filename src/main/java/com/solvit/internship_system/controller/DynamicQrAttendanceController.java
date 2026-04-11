package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.attendance.dynamicqr.AttendanceLocationDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.AttendanceRecordApiDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.InternDynamicQrStatsDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.QrDailyLimitsDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.QrTokenResponseDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.ScanRequestDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.ScanResultDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.QrExtraAccessRequestCreateDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.QrExtraAccessRequestItemDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.QrExtraAccessResolveDto;
import com.solvit.internship_system.dto.attendance.dynamicqr.SupervisorContactDto;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.security.CurrentUserResolver;
import com.solvit.internship_system.service.InternQrAttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Dynamic per-intern JWT QR + scan flow. Base path {@code /api/attendance/qr} avoids overlap with
 * {@link AdminAttendanceController} ({@code /api/attendance}) and {@link AttendanceController} ({@code /api/attendances}).
 */
@RestController
@RequestMapping("/api/attendance/qr")
@RequiredArgsConstructor
@Slf4j
public class DynamicQrAttendanceController {

    private final InternQrAttendanceService internQrAttendanceService;
    private final CurrentUserResolver currentUser;

    @GetMapping("/my-qr-token")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<QrTokenResponseDto> getMyQrToken() {
        Long internId = currentUser.requireUserId();
        return ResponseEntity.ok(internQrAttendanceService.generateTokenForIntern(internId));
    }

    /** Daily caps: new QR JWT 2×/day, PNG download 2×/day, token copy 2×/day (Kigali calendar day). */
    @GetMapping("/daily-limits")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<QrDailyLimitsDto> dailyLimits() {
        Long internId = currentUser.requireUserId();
        return ResponseEntity.ok(internQrAttendanceService.getDailyLimits(internId));
    }

    @PostMapping("/track-download")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<QrDailyLimitsDto> trackDownload() {
        Long internId = currentUser.requireUserId();
        return ResponseEntity.ok(internQrAttendanceService.recordDownload(internId));
    }

    @PostMapping("/track-token-copy")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<QrDailyLimitsDto> trackTokenCopy() {
        Long internId = currentUser.requireUserId();
        return ResponseEntity.ok(internQrAttendanceService.recordTokenCopy(internId));
    }

    @GetMapping("/supervisor-contact")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<SupervisorContactDto> supervisorContact() {
        Long internId = currentUser.requireUserId();
        return ResponseEntity.ok(internQrAttendanceService.getSupervisorContact(internId));
    }

    /** After daily QR generations are exhausted, intern asks supervisor for 1–2 extra generations (same Kigali day). */
    @PostMapping("/extra-access/request")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<Void> requestExtraQrAccess(@RequestBody(required = false) QrExtraAccessRequestCreateDto body) {
        Long internId = currentUser.requireUserId();
        internQrAttendanceService.requestExtraQrAccess(internId, body != null ? body.message() : null);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/extra-access/pending")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<List<QrExtraAccessRequestItemDto>> pendingExtraAccessRequests() {
        Long supervisorId = currentUser.requireUserId();
        return ResponseEntity.ok(internQrAttendanceService.listPendingExtraAccessRequests(supervisorId));
    }

    @PostMapping("/extra-access/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<Void> resolveExtraAccessRequest(
            @PathVariable("id") long id, @RequestBody QrExtraAccessResolveDto body) {
        Long supervisorId = currentUser.requireUserId();
        internQrAttendanceService.resolveExtraAccessRequest(supervisorId, id, body);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR')")
    public ResponseEntity<ScanResultDto> scan(@RequestBody ScanRequestDto body, HttpServletRequest request) {
        Long scannedBy = currentUser.requireUserId();
        String ip = clientIp(request);
        return ResponseEntity.ok(internQrAttendanceService.processScan(
                body.getToken(),
                scannedBy,
                body.getLocationId(),
                body.getLatitude(),
                body.getLongitude(),
                ip));
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<Page<AttendanceRecordApiDto>> myHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        Long internId = currentUser.requireUserId();
        LocalDate start = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate end = to != null ? to : LocalDate.now();
        return ResponseEntity.ok(internQrAttendanceService.getMyAttendance(internId, start, end, pageable));
    }

    @GetMapping("/my-stats")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<InternDynamicQrStatsDto> myStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long internId = currentUser.requireUserId();
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        return ResponseEntity.ok(internQrAttendanceService.getInternStats(internId, start, end));
    }

    @GetMapping("/my-interns")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR')")
    public ResponseEntity<Page<AttendanceRecordApiDto>> supervisorView(
            @RequestParam(required = false) Long internId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        Long supervisorId = currentUser.requireUserId();
        Role role = currentUser.requireRole();
        return ResponseEntity.ok(internQrAttendanceService.listForSupervisor(
                supervisorId, role, internId, date, status, pageable));
    }

    @GetMapping("/locations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttendanceLocationDto>> locations() {
        return ResponseEntity.ok(internQrAttendanceService.getActiveLocations());
    }

    @PostMapping("/locations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AttendanceLocationDto> createLocation(@RequestBody AttendanceLocationDto dto) {
        return ResponseEntity.ok(internQrAttendanceService.createLocation(dto));
    }

    private static String clientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
