package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.Attendance;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.AttendanceCalculationService;
import com.solvit.internship_system.service.AttendanceService;
import com.solvit.internship_system.service.QrAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final QrAttendanceService qrAttendanceService;
    private final JwtUtil jwtUtil;

    @PostMapping("/check-in")
    public ResponseEntity<Attendance> checkIn(@RequestHeader("Authorization") String authHeader,
                                              @RequestBody(required = false) Map<String, String> body) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        String lat = body != null ? body.get("latitude") : null;
        String lon = body != null ? body.get("longitude") : null;
        String location = body != null ? body.get("location") : null;
        return ResponseEntity.ok(attendanceService.checkIn(userId, lat, lon, location));
    }

    /** Intern presents QR scanned on-site (token must match today). */
    @PostMapping("/check-in-qr")
    @PreAuthorize("hasRole('INTERN')")
    public ResponseEntity<Attendance> checkInQr(@RequestHeader("Authorization") String authHeader,
                                                  @RequestBody Map<String, String> body) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        String token = body != null ? body.get("token") : null;
        String lat = body != null ? body.get("latitude") : null;
        String lon = body != null ? body.get("longitude") : null;
        String location = body != null ? body.get("location") : null;
        return ResponseEntity.ok(attendanceService.checkInWithQr(userId, token, lat, lon, location));
    }

    /** Display on office screen — interns must not call this (they scan the rendered QR). */
    @GetMapping("/qr-token/today")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','HR')")
    public ResponseEntity<Map<String, String>> qrTokenToday() {
        LocalDate d = AttendanceCalculationService.todayKigali();
        return ResponseEntity.ok(Map.of(
                "date", d.toString(),
                "token", qrAttendanceService.generateTokenForDate(d)
        ));
    }

    @PostMapping("/check-out")
    public ResponseEntity<Attendance> checkOut(@RequestHeader("Authorization") String authHeader,
                                               @RequestBody(required = false) Map<String, String> body) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        String lat = body != null ? body.get("latitude") : null;
        String lon = body != null ? body.get("longitude") : null;
        String location = body != null ? body.get("location") : null;
        return ResponseEntity.ok(attendanceService.checkOut(userId, lat, lon, location));
    }

    @GetMapping("/me")
    public ResponseEntity<List<Attendance>> getMyAttendance(@RequestHeader("Authorization") String authHeader,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(attendanceService.getByUserInRange(userId, start, end));
    }

    @GetMapping("/me/date/{date}")
    public ResponseEntity<Attendance> getMyByDate(@RequestHeader("Authorization") String authHeader,
                                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(attendanceService.getByUserAndDateOrNull(userId, date));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<Attendance> validate(@RequestHeader("Authorization") String authHeader,
                                               @PathVariable Long id) {
        Long supervisorId = jwtUtil.getUserIdFromToken(authHeader.substring(7));
        return ResponseEntity.ok(attendanceService.validate(id, supervisorId));
    }
}
