package com.solvit.internship_system.report;

import com.solvit.internship_system.report.model.KpiEntry;
import com.solvit.internship_system.report.model.ReportPayload;
import com.solvit.internship_system.report.model.ReportTableSection;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ExcelGeneratorService {

    public byte[] render(ReportPayload payload) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = headerStyle(wb);
            CellStyle altRowStyle = altRowStyle(wb);
            CellStyle normalStyle = normalStyle(wb);

            Sheet meta = wb.createSheet("Summary");
            int r = 0;
            Row titleRow = meta.createRow(r++);
            titleRow.createCell(0).setCellValue(payload.getPdfMainTitle());
            titleRow.getCell(0).setCellStyle(titleStyle(wb));
            meta.createRow(r++).createCell(0).setCellValue("Period: " + nullSafe(payload.getPeriodDescription()));
            meta.createRow(r++).createCell(0).setCellValue("Reference: " + nullSafe(payload.getReference()));
            meta.createRow(r++).createCell(0).setCellValue("Generated: " + nullSafe(payload.getGeneratedAtText()));
            r++;

            if (payload.getKpis() != null && !payload.getKpis().isEmpty()) {
                Row kpiHeader = meta.createRow(r++);
                kpiHeader.createCell(0).setCellValue("KPI");
                kpiHeader.createCell(1).setCellValue("Value");
                kpiHeader.getCell(0).setCellStyle(headerStyle);
                kpiHeader.getCell(1).setCellStyle(headerStyle);
                for (KpiEntry k : payload.getKpis()) {
                    Row row = meta.createRow(r++);
                    row.createCell(0).setCellValue(nullSafe(k.getLabel()));
                    row.createCell(1).setCellValue(nullSafe(k.getValue()));
                }
                r++;
            }

            for (String note : payload.getNotes()) {
                Row nr = meta.createRow(r++);
                nr.createCell(0).setCellValue("Note: " + nullSafe(note));
            }

            int sheetIdx = 0;
            for (ReportTableSection section : payload.getTables()) {
                String name = safeSheetName(section.getSectionTitle(), sheetIdx++);
                Sheet sh = wb.createSheet(name);
                writeTable(sh, section, headerStyle, altRowStyle, normalStyle);
            }

            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Excel generation failed: " + e.getMessage(), e);
        }
    }

    private void writeTable(Sheet sh, ReportTableSection section, CellStyle headerStyle, CellStyle altStyle, CellStyle normalStyle) {
        List<String> headers = section.getHeaders();
        List<List<String>> rows = section.getRows();
        if (headers == null || headers.isEmpty()) {
            return;
        }
        int r = 0;
        Row hr = sh.createRow(r++);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(nullSafe(headers.get(i)));
            c.setCellStyle(headerStyle);
        }
        int idx = 0;
        if (rows != null) {
            for (List<String> row : rows) {
                Row xr = sh.createRow(r++);
                CellStyle base = (idx % 2 == 1) ? altStyle : normalStyle;
                idx++;
                for (int j = 0; j < headers.size(); j++) {
                    Cell c = xr.createCell(j);
                    c.setCellValue(j < row.size() && row.get(j) != null ? row.get(j) : "");
                    c.setCellStyle(base);
                }
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            sh.autoSizeColumn(i);
        }
    }

    private static String safeSheetName(String title, int idx) {
        String base = title == null || title.isBlank() ? "Data" : title;
        base = base.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        return base.isEmpty() ? "Sheet" + idx : base;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static CellStyle titleStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        st.setFont(f);
        return st;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        st.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        st.setFont(f);
        return st;
    }

    private static CellStyle normalStyle(Workbook wb) {
        return wb.createCellStyle();
    }

    private static CellStyle altRowStyle(Workbook wb) {
        CellStyle st = wb.createCellStyle();
        st.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }
}
