package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.ReportHistory;
import com.solvit.internship_system.report.ReportGenerationResult;
import com.solvit.internship_system.report.ReportRequest;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.service.AdminReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','HR')")
@org.springframework.web.bind.annotation.CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class AdminReportsController {

    private final AdminReportsService adminReportsService;
    private final JwtUtil jwtUtil;

    @GetMapping("/generate")
    public ResponseEntity<byte[]> generate(
            @RequestParam String type,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long internId,
            @RequestParam(defaultValue = "CSV") String format,
            @RequestHeader("Authorization") String authHeader) {
        Long userId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        int y = year != null ? year : LocalDate.now().getYear();
        ReportGenerationResult gen = adminReportsService.generateReportWithReference(type, month, y, internId, format, userId);
        byte[] content = gen.content();
        String filename = "report-" + type.toLowerCase() + "-" + System.currentTimeMillis() + "." + (format.equalsIgnoreCase("EXCEL") ? "xlsx" : format.equalsIgnoreCase("PDF") ? "pdf" : "csv");
        String period = (month != null ? month + "/" : "") + y;
        adminReportsService.saveHistory(type, period, format, userId, filename, (long) content.length, gen.reference());
        MediaType mediaType = "PDF".equalsIgnoreCase(format) ? MediaType.APPLICATION_PDF
                : "EXCEL".equalsIgnoreCase(format) ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(content);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody ReportRequest body,
                                         @RequestHeader("Authorization") String authHeader) {
        Long userId = authHeader != null && authHeader.startsWith("Bearer ")
                ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        String fmt = body.getFormat() != null ? body.getFormat() : "CSV";
        ReportGenerationResult gen = adminReportsService.generateReportWithReference(body);
        byte[] content = gen.content();
        String ext = fmt.equalsIgnoreCase("EXCEL") ? "xlsx" : fmt.equalsIgnoreCase("PDF") ? "pdf" : "csv";
        String safeType = body.getReportType() != null ? body.getReportType().replaceAll("[^a-zA-Z0-9_-]", "") : "report";
        String filename = "report-" + safeType.toLowerCase() + "-" + System.currentTimeMillis() + "." + ext;
        String period = body.getDateFrom() != null && body.getDateTo() != null
                ? body.getDateFrom() + "—" + body.getDateTo()
                : (body.getMonth() != null && body.getYear() != null ? body.getMonth() + "/" + body.getYear() : String.valueOf(LocalDate.now().getYear()));
        adminReportsService.saveHistory(safeType, period, fmt, userId, filename, (long) content.length, gen.reference());
        MediaType mediaType = "PDF".equalsIgnoreCase(fmt) ? MediaType.APPLICATION_PDF
                : "EXCEL".equalsIgnoreCase(fmt) ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(content);
    }

    @GetMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestParam String type,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(adminReportsService.previewReport(type, month, year));
    }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewBody(@RequestBody ReportRequest body) {
        return ResponseEntity.ok(adminReportsService.previewReportBody(body));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<ReportHistory>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminReportsService.getHistory(page, size));
    }

    @DeleteMapping("/history/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHistory(@PathVariable Long id) {
        adminReportsService.deleteHistory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history/{id}/download")
    public ResponseEntity<byte[]> downloadHistory(@PathVariable Long id,
                                                  @RequestHeader("Authorization") String authHeader) {
        ReportHistory h = adminReportsService.getHistoryById(id);
        if (h == null) return ResponseEntity.notFound().build();
        Long userId = authHeader != null && authHeader.startsWith("Bearer ") ? jwtUtil.getUserIdFromToken(authHeader.substring(7)) : null;
        ReportRequest.ReportRequestBuilder rb = ReportRequest.builder()
                .reportType(h.getReportType())
                .format(h.getFormat() != null ? h.getFormat() : "CSV");
        String per = h.getPeriod();
        if (per != null && per.contains("/")) {
            String[] parts = per.split("/");
            if (parts.length >= 2) {
                try {
                    rb.month(Integer.parseInt(parts[0].trim()));
                    rb.year(Integer.parseInt(parts[1].trim()));
                    rb.period("MONTHLY");
                } catch (NumberFormatException ignored) {
                    // keep defaults
                }
            }
        }
        ReportGenerationResult gen = adminReportsService.generateReportWithReference(rb.build());
        byte[] content = gen.content();
        String fmt = h.getFormat() != null ? h.getFormat() : "CSV";
        String ext = fmt.equalsIgnoreCase("EXCEL") ? "xlsx" : fmt.equalsIgnoreCase("PDF") ? "pdf" : "csv";
        MediaType mediaType = "PDF".equalsIgnoreCase(fmt) ? MediaType.APPLICATION_PDF
                : "EXCEL".equalsIgnoreCase(fmt) ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        String fname = h.getFilePath() != null && h.getFilePath().endsWith(ext)
                ? h.getFilePath()
                : "report-" + id + "." + ext;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .contentType(mediaType)
                .body(content);
    }
}
