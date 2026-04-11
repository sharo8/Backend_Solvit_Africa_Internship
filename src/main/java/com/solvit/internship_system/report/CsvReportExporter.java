package com.solvit.internship_system.report;

import com.solvit.internship_system.report.model.KpiEntry;
import com.solvit.internship_system.report.model.ReportPayload;
import com.solvit.internship_system.report.model.ReportTableSection;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class CsvReportExporter {

    public byte[] render(ReportPayload payload) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        csvLine(sb, "Title", payload.getPdfMainTitle());
        csvLine(sb, "Period", payload.getPeriodDescription());
        csvLine(sb, "Reference", payload.getReference());
        csvLine(sb, "Generated", payload.getGeneratedAtText());
        sb.append("\n");

        if (payload.getKpis() != null) {
            sb.append("KPI,Value\n");
            for (KpiEntry k : payload.getKpis()) {
                csvLine(sb, k.getLabel(), k.getValue());
            }
            sb.append("\n");
        }

        for (ReportTableSection table : payload.getTables()) {
            if (table.getSectionTitle() != null && !table.getSectionTitle().isBlank()) {
                sb.append("# ").append(escape(table.getSectionTitle())).append("\n");
            }
            List<String> h = table.getHeaders();
            if (h == null || h.isEmpty()) {
                continue;
            }
            sb.append(String.join(",", h.stream().map(this::escape).toList())).append("\n");
            if (table.getRows() != null) {
                for (List<String> row : table.getRows()) {
                    for (int i = 0; i < h.size(); i++) {
                        if (i > 0) {
                            sb.append(",");
                        }
                        String cell = i < row.size() && row.get(i) != null ? row.get(i) : "";
                        sb.append(escape(cell));
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        for (String note : payload.getNotes()) {
            csvLine(sb, "Note", note);
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void csvLine(StringBuilder sb, String a, String b) {
        sb.append(escape(a)).append(",").append(escape(b)).append("\n");
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
