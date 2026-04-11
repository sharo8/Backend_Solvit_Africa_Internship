package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.ReportHistory;
import com.solvit.internship_system.report.ProfessionalReportService;
import com.solvit.internship_system.report.ReportGenerationResult;
import com.solvit.internship_system.report.ReportRequest;
import com.solvit.internship_system.repository.ReportHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminReportsService {

    private final ReportHistoryRepository reportHistoryRepository;
    private final AdminDashboardService adminDashboardService;
    private final ProfessionalReportService professionalReportService;

    public ReportGenerationResult generateReportWithReference(String type, Integer month, Integer year, Long internId, String format, Long generatedBy) {
        ReportRequest req = ReportRequest.builder()
                .reportType(type)
                .format(format != null ? format : "CSV")
                .month(month)
                .year(year != null ? year : java.time.LocalDate.now().getYear())
                .internId(internId)
                .period(month != null ? "MONTHLY" : null)
                .build();
        return professionalReportService.generate(req);
    }

    public byte[] generateReport(String type, Integer month, Integer year, Long internId, String format, Long generatedBy) {
        return generateReportWithReference(type, month, year, internId, format, generatedBy).content();
    }

    public ReportGenerationResult generateReportWithReference(ReportRequest request) {
        return professionalReportService.generate(request);
    }

    public byte[] generateReport(ReportRequest request) {
        return professionalReportService.generate(request).content();
    }

    public Map<String, Object> previewReport(String type, Integer month, Integer year) {
        if (looksLegacy(type)) {
            if ("ATTENDANCE".equalsIgnoreCase(type)) {
                return Map.of("attendanceTrend", adminDashboardService.getAttendanceTrend(30));
            }
            if ("PERFORMANCE".equalsIgnoreCase(type) || "COHORT".equalsIgnoreCase(type)) {
                return Map.of("performanceByCohort", adminDashboardService.getPerformanceByCohort());
            }
            return Map.of("kpis", adminDashboardService.getKpis());
        }
        ReportRequest req = ReportRequest.builder()
                .reportType(type)
                .month(month)
                .year(year != null ? year : java.time.LocalDate.now().getYear())
                .period(month != null ? "MONTHLY" : null)
                .build();
        var payload = professionalReportService.buildPreview(req);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", payload.getPdfMainTitle());
        out.put("period", payload.getPeriodDescription());
        out.put("reference", payload.getReference());
        out.put("kpis", payload.getKpis());
        out.put("tableSectionCount", payload.getTables() != null ? payload.getTables().size() : 0);
        out.put("notes", payload.getNotes());
        return out;
    }

    public Map<String, Object> previewReportBody(ReportRequest request) {
        var payload = professionalReportService.buildPreview(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", payload.getPdfMainTitle());
        out.put("period", payload.getPeriodDescription());
        out.put("reference", payload.getReference());
        out.put("kpis", payload.getKpis());
        out.put("tables", payload.getTables());
        out.put("notes", payload.getNotes());
        return out;
    }

    private static boolean looksLegacy(String type) {
        if (type == null) {
            return true;
        }
        String u = type.trim().toUpperCase();
        return "ATTENDANCE".equals(u) || "PERFORMANCE".equals(u) || "COHORT".equals(u) || "AT_RISK".equals(u)
                || "TASKS".equals(u) || "FEEDBACK".equals(u) || "FULL".equals(u);
    }

    @Transactional
    public ReportHistory saveHistory(String reportType, String period, String format, Long generatedBy, String filePath, long fileSize, String referenceNumber) {
        ReportHistory h = ReportHistory.builder()
                .reportType(reportType)
                .period(period)
                .referenceNumber(referenceNumber)
                .format(format)
                .generatedBy(generatedBy)
                .generatedAt(Instant.now())
                .filePath(filePath)
                .fileSize(fileSize)
                .active(true)
                .build();
        return reportHistoryRepository.save(h);
    }

    /** @deprecated use {@link #saveHistory(String, String, String, Long, String, long, String)} */
    @Transactional
    public ReportHistory saveHistory(String reportType, String period, String format, Long generatedBy, String filePath, long fileSize) {
        return saveHistory(reportType, period, format, generatedBy, filePath, fileSize, null);
    }

    public Page<ReportHistory> getHistory(int page, int size) {
        return reportHistoryRepository.findByActiveTrueOrderByGeneratedAtDesc(PageRequest.of(page, size));
    }

    public Page<ReportHistory> getHistoryForUser(Long userId, int page, int size) {
        return reportHistoryRepository.findByGeneratedByAndActiveTrueOrderByGeneratedAtDesc(userId, PageRequest.of(page, size));
    }

    public ReportHistory getHistoryById(Long id) {
        return reportHistoryRepository.findById(id).orElse(null);
    }

    @Transactional
    public void deleteHistory(Long id) {
        reportHistoryRepository.findById(id).ifPresent(h -> {
            h.setActive(false);
            reportHistoryRepository.save(h);
        });
    }
}
