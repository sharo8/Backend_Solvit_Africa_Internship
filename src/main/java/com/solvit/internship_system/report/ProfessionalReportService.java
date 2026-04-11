package com.solvit.internship_system.report;

import com.solvit.internship_system.report.model.ReportPayload;
import com.solvit.internship_system.repository.ReportHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ProfessionalReportService {

    private final ReportPayloadBuilder reportPayloadBuilder;
    private final PdfGeneratorService pdfGeneratorService;
    private final ExcelGeneratorService excelGeneratorService;
    private final CsvReportExporter csvReportExporter;
    private final ReportHistoryRepository reportHistoryRepository;

    public ReportGenerationResult generate(ReportRequest req) {
        String reference = allocateReference();
        ReportPayload payload = reportPayloadBuilder.build(req, reference);
        ReportFormat fmt = ReportFormat.fromApi(req.getFormat());
        byte[] bytes = switch (fmt) {
            case PDF -> pdfGeneratorService.render(payload);
            case EXCEL -> excelGeneratorService.render(payload);
            case CSV -> csvReportExporter.render(payload);
        };
        return new ReportGenerationResult(bytes, reference);
    }

    public byte[] generateBytes(ReportRequest req) {
        return generate(req).content();
    }

    public ReportPayload buildPreview(ReportRequest req) {
        return reportPayloadBuilder.build(req, "RPT-PREVIEW");
    }

    /**
     * Next reference for the current calendar month: RPT-YYYY-MM-SEQ (seq = existing reports this month + 1).
     */
    public String allocateReference() {
        ZonedDateTime z = ZonedDateTime.now(ZoneId.systemDefault());
        int y = z.getYear();
        int m = z.getMonthValue();
        ZonedDateTime start = z.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        Instant startInst = start.toInstant();
        Instant endInst = start.plusMonths(1).toInstant();
        long seq = reportHistoryRepository.countByActiveTrueAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(startInst, endInst) + 1;
        return String.format(java.util.Locale.US, "RPT-%d-%02d-%03d", y, m, seq);
    }
}
