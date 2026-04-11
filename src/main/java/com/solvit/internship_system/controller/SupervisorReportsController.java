package com.solvit.internship_system.controller;

import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.ReportHistory;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.report.ReportGenerationResult;
import com.solvit.internship_system.report.ReportRequest;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.ProjectGroupRepository;
import com.solvit.internship_system.security.CurrentUserPrincipal;
import com.solvit.internship_system.service.AdminReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Report generation and history scoped to the logged-in supervisor.
 */
@RestController
@RequestMapping("/api/supervisor/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISOR')")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"}, allowCredentials = "true")
public class SupervisorReportsController {

    private final AdminReportsService adminReportsService;
    private final InternProfileRepository internProfileRepository;
    private final ProjectGroupRepository projectGroupRepository;

    private void validateAndScopeRequest(Long supervisorUserId, ReportRequest body) {
        body.setSupervisorId(supervisorUserId);
        if (body.getInternId() != null) {
            InternProfile p = internProfileRepository.findByUser_Id(body.getInternId())
                    .orElseThrow(() -> new ResourceNotFoundException("Intern profile", body.getInternId()));
            if (p.getSupervisorUserId() == null || !p.getSupervisorUserId().equals(supervisorUserId)) {
                throw new BadRequestException("Selected intern is not under your supervision");
            }
        }
        if (body.getGroupId() != null) {
            ProjectGroup g = projectGroupRepository.findById(body.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group", body.getGroupId()));
            if (g.getSupervisor() == null || !g.getSupervisor().getId().equals(supervisorUserId)) {
                throw new BadRequestException("Selected cohort is not managed by you");
            }
        }
    }

    @PostMapping("/export")
    @Transactional
    public ResponseEntity<byte[]> export(@RequestBody ReportRequest body,
                                         @AuthenticationPrincipal CurrentUserPrincipal principal) {
        Long userId = principal.getUserId();
        validateAndScopeRequest(userId, body);
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

    @PostMapping("/preview")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> previewBody(@RequestBody ReportRequest body,
                                                           @AuthenticationPrincipal CurrentUserPrincipal principal) {
        validateAndScopeRequest(principal.getUserId(), body);
        return ResponseEntity.ok(adminReportsService.previewReportBody(body));
    }

    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<ReportHistory>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ResponseEntity.ok(adminReportsService.getHistoryForUser(principal.getUserId(), page, size));
    }

    @GetMapping("/history/{id}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadHistory(@PathVariable Long id,
                                                  @AuthenticationPrincipal CurrentUserPrincipal principal) {
        ReportHistory h = adminReportsService.getHistoryById(id);
        if (h == null) return ResponseEntity.notFound().build();
        if (h.getGeneratedBy() == null || !h.getGeneratedBy().equals(principal.getUserId())) {
            return ResponseEntity.notFound().build();
        }
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
        rb.supervisorId(principal.getUserId());
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
