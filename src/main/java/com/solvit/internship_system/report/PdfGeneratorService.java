package com.solvit.internship_system.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.solvit.internship_system.report.model.HorizontalBarChartSpec;
import com.solvit.internship_system.report.model.HorizontalBarValueMode;
import com.solvit.internship_system.report.model.KpiEntry;
import com.solvit.internship_system.report.model.ReportPayload;
import com.solvit.internship_system.report.model.ReportTableSection;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PdfGeneratorService {

    private static final float MARGIN_LR = 42f;
    private static final float MARGIN_TB = 36f;
    private static final String FOOTER_TEXT = "Internship Management System | Confidential";

    public byte[] render(ReportPayload payload) {
        try {
            Document document = new Document(PageSize.A4, MARGIN_LR, MARGIN_LR, MARGIN_TB, MARGIN_TB + 18);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            byte[] logoBytes = loadLogoBytes();
            writer.setPageEvent(new SolvitPdfPageEvent(logoBytes));
            document.open();

            buildCoverPage(document, payload, logoBytes);
            document.newPage();
            addHeader(document, payload, logoBytes);
            addBlueBand(document);
            addKpis(document, payload.getKpis());
            for (ReportTableSection table : payload.getTables()) {
                addSectionTitle(document, table.getSectionTitle());
                addDataTable(document, table.getHeaders(), table.getRows());
            }
            for (HorizontalBarChartSpec chart : payload.getHorizontalBarCharts()) {
                addSectionTitle(document, chart.getTitle());
                addHorizontalBarChart(document, chart);
            }
            for (String note : payload.getNotes()) {
                addNoteBox(document, note);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private byte[] loadLogoBytes() {
        try (InputStream input = openLogoStream()) {
            return input != null ? input.readAllBytes() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private InputStream openLogoStream() {
        InputStream logoStream = getClass().getResourceAsStream("/static/logo.png");
        if (logoStream == null) {
            logoStream = getClass().getClassLoader().getResourceAsStream("static/logo.png");
        }
        return logoStream;
    }

    private Image logoImage(byte[] logoBytes, float maxW, float maxH) {
        if (logoBytes == null) {
            return null;
        }
        try {
            Image image = Image.getInstance(logoBytes);
            image.scaleToFit(maxW, maxH);
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void addHeader(Document document, ReportPayload payload, byte[] logoBytes) throws Exception {
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{2.6f, 1.4f});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setBackgroundColor(ReportBranding.DARK_NAVY);
        left.setPaddingTop(24);
        left.setPaddingBottom(24);
        left.setPaddingLeft(36);
        left.setPaddingRight(12);

        PdfPTable brand = new PdfPTable(2);
        brand.setWidths(new float[]{0.6f, 1.4f});
        brand.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setBackgroundColor(ReportBranding.DARK_NAVY);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logo = logoImage(logoBytes, 80f, 40f);
        if (logo != null) {
            logoCell.addElement(logo);
        } else {
            logoCell.addElement(new Phrase(""));
        }
        brand.addCell(logoCell);

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setBackgroundColor(ReportBranding.DARK_NAVY);
        Font org = new Font(Font.HELVETICA, 15, Font.BOLD, Color.WHITE);
        Font sub = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, ReportBranding.LIGHT_TEXT);
        Paragraph p = new Paragraph();
        p.add(new Chunk("SOLVIT AFRICA\n", org));
        p.add(new Chunk("Management System", sub));
        textCell.addElement(p);
        brand.addCell(textCell);

        left.addElement(brand);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setBackgroundColor(ReportBranding.DARK_NAVY);
        right.setPaddingTop(24);
        right.setPaddingBottom(24);
        right.setPaddingLeft(12);
        right.setPaddingRight(36);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Font w = new Font(Font.HELVETICA, 11.5f, Font.BOLD, Color.WHITE);
        Font wSmall = new Font(Font.HELVETICA, 10f, Font.NORMAL, ReportBranding.LIGHT_TEXT);
        Font wXSmallItalic = new Font(Font.HELVETICA, 10, Font.ITALIC, ReportBranding.LIGHT_TEXT);
        Paragraph pr = new Paragraph();
        pr.setAlignment(Element.ALIGN_RIGHT);
        pr.add(new Chunk((payload.getPdfMainTitle() != null ? payload.getPdfMainTitle() : "REPORT") + "\n", w));
        pr.add(new Chunk((payload.getPeriodDescription() != null ? payload.getPeriodDescription() : "") + "\n", wSmall));
        pr.add(new Chunk("Generated: " + (payload.getGeneratedAtText() != null ? payload.getGeneratedAtText() : "") + "\n",
                new Font(Font.HELVETICA, 10, Font.NORMAL, ReportBranding.LIGHT_TEXT)));
        pr.add(new Chunk("Ref: " + (payload.getReference() != null ? payload.getReference() : ""), wXSmallItalic));
        right.addElement(pr);

        outer.addCell(left);
        outer.addCell(right);
        document.add(outer);

        PdfPTable border = new PdfPTable(1);
        border.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(ReportBranding.BLUE);
        c.setBorder(Rectangle.NO_BORDER);
        c.setFixedHeight(3f);
        border.addCell(c);
        document.add(border);
    }

    private void addBlueBand(Document document) throws DocumentException {
        PdfPTable band = new PdfPTable(1);
        band.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase("Internship Monitoring System — Official Report",
                new Font(Font.HELVETICA, 10.5f, Font.BOLD, Color.WHITE)));
        c.setBackgroundColor(ReportBranding.BLUE);
        c.setBorder(Rectangle.NO_BORDER);
        c.setFixedHeight(30f);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        band.addCell(c);
        document.add(band);
        document.add(Chunk.NEWLINE);
    }

    private void addKpis(Document document, List<KpiEntry> kpis) throws DocumentException {
        if (kpis == null || kpis.isEmpty()) {
            return;
        }
        int cols = 4;
        PdfPTable grid = new PdfPTable(cols);
        grid.setWidthPercentage(100);
        grid.setSpacingAfter(12f);

        for (KpiEntry k : kpis) {
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(ReportBranding.LIGHT_BLUE_BG);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(ReportBranding.BORDER_SLATE);
            cell.setBorderWidth(0.5f);
            cell.setCellEvent(new LeftBlueBorderEvent(3f, ReportBranding.BLUE));
            cell.setPaddingTop(12);
            cell.setPaddingBottom(12);
            cell.setPaddingLeft(14);
            cell.setPaddingRight(14);
            cell.setVerticalAlignment(Element.ALIGN_TOP);
            cell.setMinimumHeight(58f);
            String rawVal = k.getValue() != null ? k.getValue() : "—";
            float valPt = kpiValueFontPoints(rawVal);
            Font valFont = new Font(Font.HELVETICA, valPt, Font.BOLD, ReportBranding.DARK_NAVY);
            Font labFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, ReportBranding.SLATE_500);
            Paragraph pg = new Paragraph();
            pg.setLeading(0f, 1.22f);
            pg.setAlignment(Element.ALIGN_LEFT);
            pg.add(new Chunk(rawVal + "\n", valFont));
            pg.add(new Chunk(k.getLabel() != null ? k.getLabel().toUpperCase(Locale.ROOT) : "", labFont));
            cell.addElement(pg);
            grid.addCell(cell);
        }
        int rem = kpis.size() % cols;
        if (rem != 0) {
            for (int p = 0; p < cols - rem; p++) {
                PdfPCell empty = new PdfPCell();
                empty.setBorder(Rectangle.NO_BORDER);
                grid.addCell(empty);
            }
        }
        document.add(grid);
    }

    private void addSectionTitle(Document document, String title) throws DocumentException {
        if (title == null || title.isBlank()) {
            return;
        }
        Font f = new Font(Font.HELVETICA, 11f, Font.BOLD, ReportBranding.DARK_NAVY);
        Paragraph p = new Paragraph(title.toUpperCase(Locale.ROOT), f);
        p.setSpacingBefore(14f);
        p.setSpacingAfter(7f);
        document.add(p);
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setFixedHeight(1.5f);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(ReportBranding.BORDER_SLATE);
        c.setBorderWidthBottom(1.5f);
        line.addCell(c);
        document.add(line);
    }

    private void addDataTable(Document document, List<String> headers, List<List<String>> rows) throws DocumentException {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        table.setSpacingAfter(12f);
        Font headFont = new Font(Font.HELVETICA, 9.5f, Font.BOLD, Color.WHITE);
        int statusCol = findStatusColumn(headers);
        for (String h : headers) {
            PdfPCell hc = new PdfPCell(new Phrase(h != null ? h.toUpperCase() : "", headFont));
            hc.setBackgroundColor(ReportBranding.DARK_NAVY);
            hc.setBorderColor(ReportBranding.BORDER_SLATE);
            hc.setBorderWidth(0.5f);
            hc.setPaddingTop(10);
            hc.setPaddingBottom(10);
            hc.setPaddingLeft(12);
            hc.setPaddingRight(12);
            table.addCell(hc);
        }
        Font cellFont = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, ReportBranding.SLATE_700);
        int i = 0;
        if (rows != null) {
            for (List<String> row : rows) {
                Color bg = (i % 2 == 0) ? Color.WHITE : ReportBranding.ROW_ALT;
                i++;
                for (int j = 0; j < headers.size(); j++) {
                    String text = j < row.size() && row.get(j) != null ? row.get(j) : "";
                    PdfPCell cc;
                    if (j == statusCol) {
                        cc = buildStatusBadgeCell(text, bg);
                    } else {
                        cc = new PdfPCell(new Phrase(text, cellFont));
                    }
                    cc.setBackgroundColor(bg);
                    cc.setBorderColor(ReportBranding.BORDER_SLATE);
                    cc.setBorderWidth(0.5f);
                    cc.setPaddingTop(8);
                    cc.setPaddingBottom(8);
                    cc.setPaddingLeft(12);
                    cc.setPaddingRight(12);
                    table.addCell(cc);
                }
            }
        }
        document.add(table);
    }

    private int findStatusColumn(List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null && header.trim().equalsIgnoreCase("status")) {
                return i;
            }
        }
        return -1;
    }

    private PdfPCell buildStatusBadgeCell(String text, Color rowBg) {
        BadgeStyle style = BadgeStyle.from(text);
        Font badgeFont = new Font(Font.HELVETICA, 9, style.bold ? Font.BOLD : Font.NORMAL, style.textColor);
        PdfPCell cell = new PdfPCell(new Phrase(text, badgeFont));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setCellEvent(new RoundedBadgeBackground(style.bgColor, rowBg));
        return cell;
    }

    private void addHorizontalBarChart(Document document, HorizontalBarChartSpec spec) throws DocumentException {
        if (spec.getValues() == null || spec.getValues().isEmpty()) {
            return;
        }
        double max = spec.getValues().values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) {
            max = 1;
        }
        Color barColor = chartColor(spec);
        Font lf = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, ReportBranding.SLATE_700);
        Font vf = new Font(Font.HELVETICA, 9.5f, Font.BOLD, ReportBranding.DARK_NAVY);
        Font axisFont = new Font(Font.HELVETICA, 8f, Font.NORMAL, ReportBranding.SLATE_400);
        HorizontalBarValueMode mode = spec.getValueMode() != null ? spec.getValueMode() : HorizontalBarValueMode.PERCENT;

        PdfPTable axisRow = new PdfPTable(3);
        axisRow.setWidthPercentage(100);
        axisRow.setWidths(new float[]{3.2f, 5.8f, 1.0f});
        axisRow.setSpacingAfter(4f);
        PdfPCell axLeft = new PdfPCell(new Phrase("", axisFont));
        axLeft.setBorder(Rectangle.NO_BORDER);
        PdfPCell axMid = new PdfPCell(new Phrase(axisTrackCaption(mode, max), axisFont));
        axMid.setBorder(Rectangle.NO_BORDER);
        axMid.setBackgroundColor(ReportBranding.ROW_ALT);
        axMid.setPaddingTop(4f);
        axMid.setPaddingBottom(6f);
        axMid.setPaddingLeft(8f);
        PdfPCell axRight = new PdfPCell(new Phrase("", axisFont));
        axRight.setBorder(Rectangle.NO_BORDER);
        axisRow.addCell(axLeft);
        axisRow.addCell(axMid);
        axisRow.addCell(axRight);
        document.add(axisRow);

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{3.2f, 5.8f, 1.0f});
        t.setSpacingAfter(4f);
        for (Map.Entry<String, Double> e : spec.getValues().entrySet()) {
            PdfPCell l = new PdfPCell(new Phrase(e.getKey(), lf));
            l.setBorder(Rectangle.NO_BORDER);
            l.setHorizontalAlignment(Element.ALIGN_RIGHT);
            l.setVerticalAlignment(Element.ALIGN_MIDDLE);
            l.setPaddingRight(8);
            l.setPaddingBottom(11);
            t.addCell(l);

            PdfPCell barCell = new PdfPCell();
            barCell.setBorder(Rectangle.NO_BORDER);
            barCell.setCellEvent(new BarCellEvent(e.getValue() / max, barColor));
            barCell.setFixedHeight(17f);
            barCell.setPaddingBottom(11);
            t.addCell(barCell);

            String formatted = formatBarValue(e.getValue(), mode);
            PdfPCell valueCell = new PdfPCell(new Phrase(formatted, vf));
            valueCell.setBorder(Rectangle.NO_BORDER);
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            valueCell.setPaddingLeft(8);
            valueCell.setPaddingBottom(11);
            t.addCell(valueCell);
        }
        document.add(t);

        Paragraph cap = new Paragraph(barChartFootnote(mode, max),
                new Font(Font.HELVETICA, 8f, Font.ITALIC, ReportBranding.SLATE_500));
        cap.setSpacingAfter(10f);
        document.add(cap);
    }

    private static float kpiValueFontPoints(String value) {
        int len = value.length();
        if (len > 52) {
            return 10f;
        }
        if (len > 36) {
            return 12f;
        }
        if (len > 22) {
            return 15f;
        }
        if (len > 14) {
            return 18f;
        }
        return 20f;
    }

    private static String formatBarValue(double v, HorizontalBarValueMode mode) {
        HorizontalBarValueMode m = mode != null ? mode : HorizontalBarValueMode.PERCENT;
        return switch (m) {
            case PERCENT -> String.format(Locale.US, "%.1f%%", v);
            case SCORE -> String.format(Locale.US, "%.1f", v);
            case COUNT -> String.format(Locale.US, "%.0f", v);
        };
    }

    private static String axisTrackCaption(HorizontalBarValueMode mode, double max) {
        HorizontalBarValueMode m = mode != null ? mode : HorizontalBarValueMode.PERCENT;
        if (m == HorizontalBarValueMode.COUNT) {
            return String.format(Locale.US, "0 — max %s", formatBarValue(max, HorizontalBarValueMode.COUNT));
        }
        if (m == HorizontalBarValueMode.SCORE) {
            return "0 —25 — 50 — 75 — 100";
        }
        return "0% — 25% — 50% — 75% — 100%";
    }

    private static String barChartFootnote(HorizontalBarValueMode mode, double max) {
        HorizontalBarValueMode m = mode != null ? mode : HorizontalBarValueMode.PERCENT;
        return switch (m) {
            case PERCENT -> "Bar length is scaled to the highest value in this chart (not always 100%).";
            case SCORE -> "Composite-style score on a 0–100 scale; bar length follows the best value in this set.";
            case COUNT -> String.format(Locale.US,
                    "Absolute counts; longest bar = %s. Values are not percentages.",
                    formatBarValue(max, HorizontalBarValueMode.COUNT));
        };
    }

    private Color chartColor(HorizontalBarChartSpec spec) {
        String title = spec.getTitle() != null ? spec.getTitle().toLowerCase() : "";
        if (title.contains("supervisor")) {
            return ReportBranding.VIOLET;
        }
        if (spec.getBarHexColor() != null && spec.getBarHexColor().startsWith("#") && spec.getBarHexColor().length() >= 7) {
            try {
                int r = Integer.parseInt(spec.getBarHexColor().substring(1, 3), 16);
                int g = Integer.parseInt(spec.getBarHexColor().substring(3, 5), 16);
                int b = Integer.parseInt(spec.getBarHexColor().substring(5, 7), 16);
                return new Color(r, g, b);
            } catch (Exception ignored) {
                return ReportBranding.BLUE;
            }
        }
        return ReportBranding.BLUE;
    }

    private void addNoteBox(Document document, String note) throws DocumentException {
        if (note == null || note.isBlank()) {
            return;
        }
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        boolean critical = note.toLowerCase().contains("critical");
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(critical ? ReportBranding.ROSE_BG : ReportBranding.AMBER_BG);
        c.setBorder(Rectangle.NO_BORDER);
        c.setCellEvent(new LeftBlueBorderEvent(3f, critical ? ReportBranding.ROSE_BORDER : ReportBranding.AMBER_BORDER));
        c.setPaddingTop(8);
        c.setPaddingBottom(8);
        c.setPaddingLeft(10);
        c.setPaddingRight(10);
        Paragraph title = new Paragraph("ALERTS & NOTES",
                new Font(Font.HELVETICA, 10, Font.BOLD, critical ? ReportBranding.ROSE_TEXT : ReportBranding.AMBER_TITLE));
        title.setSpacingAfter(4f);
        c.addElement(title);
        c.addElement(new Paragraph(note, new Font(Font.HELVETICA, 10, Font.NORMAL,
                critical ? ReportBranding.ROSE_TEXT : ReportBranding.AMBER_TEXT)));
        box.addCell(c);
        box.setSpacingAfter(8f);
        document.add(box);
    }

    private void buildCoverPage(Document doc, ReportPayload payload, byte[] logoBytes) throws DocumentException {
        Paragraph spacerTop = new Paragraph(" ");
        spacerTop.setSpacingBefore(80f);
        doc.add(spacerTop);

        Image logo = logoImage(logoBytes, 160f, 80f);
        if (logo != null) {
            logo.setAlignment(Image.ALIGN_CENTER);
            doc.add(logo);
        }

        PdfPTable decoLine = new PdfPTable(1);
        decoLine.setTotalWidth(60f);
        decoLine.setLockedWidth(true);
        PdfPCell decoCell = new PdfPCell();
        decoCell.setBorder(Rectangle.NO_BORDER);
        decoCell.setBackgroundColor(ReportBranding.BLUE);
        decoCell.setFixedHeight(4f);
        decoLine.addCell(decoCell);
        decoLine.setHorizontalAlignment(Element.ALIGN_CENTER);
        doc.add(decoLine);

        Paragraph title = new Paragraph(payload.getPdfMainTitle() != null ? payload.getPdfMainTitle() : "REPORT",
                new Font(Font.HELVETICA, 22, Font.BOLD, ReportBranding.DARK_NAVY));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(32f);
        doc.add(title);

        Paragraph period = new Paragraph(payload.getPeriodDescription() != null ? payload.getPeriodDescription() : "",
                new Font(Font.HELVETICA, 13, Font.NORMAL, ReportBranding.SLATE_500));
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingBefore(10f);
        doc.add(period);

        PdfPTable separator = new PdfPTable(1);
        separator.setWidthPercentage(100);
        separator.setSpacingBefore(36f);
        PdfPCell sepCell = new PdfPCell();
        sepCell.setBorder(Rectangle.BOTTOM);
        sepCell.setBorderWidthBottom(0.5f);
        sepCell.setBorderColorBottom(ReportBranding.BORDER_SLATE);
        sepCell.setFixedHeight(1f);
        separator.addCell(sepCell);
        doc.add(separator);

        Paragraph details = new Paragraph(
                "Generated on: " + (payload.getGeneratedAtText() != null ? payload.getGeneratedAtText() : "") +
                        " | Generated by: System Admin",
                new Font(Font.HELVETICA, 9.5f, Font.NORMAL, ReportBranding.SLATE_500));
        details.setAlignment(Element.ALIGN_CENTER);
        details.setSpacingBefore(100f);
        doc.add(details);

        Paragraph ref = new Paragraph("Reference: " + (payload.getReference() != null ? payload.getReference() : ""),
                new Font(Font.HELVETICA, 10.5f, Font.BOLD, ReportBranding.BLUE));
        ref.setAlignment(Element.ALIGN_CENTER);
        ref.setSpacingBefore(6f);
        doc.add(ref);
    }

    private static class SolvitPdfPageEvent extends PdfPageEventHelper {
        private final byte[] logoBytes;
        private PdfTemplate totalTemplate;
        private BaseFont footerFont;

        SolvitPdfPageEvent(byte[] logoBytes) {
            this.logoBytes = logoBytes;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalTemplate = writer.getDirectContent().createTemplate(50, 12);
            try {
                footerFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception ignored) {
                footerFont = null;
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                if (writer.getPageNumber() == 1) {
                    addCoverWatermark(writer, document);
                    return;
                }
                addMainWatermark(writer, document);
                PdfContentByte cb = writer.getDirectContent();
                float left = document.leftMargin();
                float right = document.getPageSize().getWidth() - document.rightMargin();
                float footerTopY = document.bottom() - 2;
                float y = footerTopY - 10;

                cb.saveState();
                cb.setColorStroke(ReportBranding.BORDER_SLATE);
                cb.setLineWidth(0.5f);
                cb.moveTo(left, footerTopY);
                cb.lineTo(right, footerTopY);
                cb.stroke();
                cb.restoreState();

                if (footerFont == null) {
                    return;
                }
                cb.beginText();
                cb.setFontAndSize(footerFont, 9);
                cb.setColorFill(ReportBranding.SLATE_400);
                cb.showTextAligned(Element.ALIGN_LEFT, FOOTER_TEXT, left, y, 0);

                int visiblePage = writer.getPageNumber() - 1;
                String pagePrefix = "Page " + visiblePage + " / ";
                cb.setColorFill(Color.WHITE);
                float rightBoxW = 64f;
                float rightBoxH = 14f;
                float rightBoxX = right - rightBoxW;
                float rightBoxY = y - 3f;
                cb.endText();
                cb.saveState();
                cb.setColorFill(ReportBranding.BLUE);
                cb.roundRectangle(rightBoxX, rightBoxY, rightBoxW, rightBoxH, 6f);
                cb.fill();
                cb.restoreState();
                cb.beginText();
                cb.setFontAndSize(footerFont, 9);
                cb.setColorFill(Color.WHITE);
                cb.showTextAligned(Element.ALIGN_LEFT, pagePrefix, rightBoxX + 8, y, 0);
                cb.addTemplate(totalTemplate, rightBoxX + 42, y - 2);
                cb.endText();

                if (logoBytes != null) {
                    Image footerLogo = Image.getInstance(logoBytes);
                    footerLogo.scaleToFit(20f, 10f);
                    float centerX = document.getPageSize().getWidth() / 2f;
                    footerLogo.setAbsolutePosition(centerX - 10f, y - 4f);
                    writer.getDirectContent().addImage(footerLogo);
                }
            } catch (Exception ignored) {
                // skip footer on failure
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                if (footerFont == null || totalTemplate == null) {
                    return;
                }
                int totalVisiblePages = Math.max(writer.getPageNumber() - 2, 1);
                totalTemplate.beginText();
                totalTemplate.setFontAndSize(footerFont, 9);
                totalTemplate.showText(String.valueOf(totalVisiblePages));
                totalTemplate.endText();
            } catch (Exception ignored) {
                // ignore
            }
        }

        private void addMainWatermark(PdfWriter writer, Document document) {
            // Watermark intentionally disabled per UI request.
        }

        private void addCoverWatermark(PdfWriter writer, Document document) {
            PdfContentByte under = writer.getDirectContentUnder();
            under.saveState();
            under.beginText();
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                under.setFontAndSize(bf, 72);
                under.setColorFill(ReportBranding.COVER_WATERMARK);
                under.showTextAligned(Element.ALIGN_CENTER, "CONFIDENTIAL",
                        document.getPageSize().getWidth() / 2f, document.getPageSize().getHeight() / 2f, 45);
            } catch (Exception ignored) {
                // ignore
            } finally {
                under.endText();
                under.restoreState();
            }
        }
    }

    private static class LeftBlueBorderEvent implements PdfPCellEvent {
        private final float width;
        private final Color color;

        LeftBlueBorderEvent(float width, Color color) {
            this.width = width;
            this.color = color;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
            cb.saveState();
            cb.setColorStroke(color);
            cb.setLineWidth(width);
            cb.moveTo(position.getLeft() + width / 2, position.getBottom());
            cb.lineTo(position.getLeft() + width / 2, position.getTop());
            cb.stroke();
            cb.restoreState();
        }
    }

    private static class BarCellEvent implements PdfPCellEvent {
        private final double ratio;
        private final Color color;

        BarCellEvent(double ratio, Color color) {
            this.ratio = Math.max(0, Math.min(1, ratio));
            this.color = color;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            cb.saveState();
            cb.setColorFill(ReportBranding.BAR_BG);
            cb.roundRectangle(position.getLeft() + 1, position.getBottom() + 1,
                    position.getWidth() - 2, position.getHeight() - 2, 4);
            cb.fill();
            cb.setColorFill(color);
            float w = (float) (position.getWidth() * ratio);
            cb.roundRectangle(position.getLeft() + 1, position.getBottom() + 1, Math.max(w - 2, 2), position.getHeight() - 2, 4);
            cb.fill();
            cb.restoreState();
        }
    }

    private record BadgeStyle(Color bgColor, Color textColor, boolean bold) {
        static BadgeStyle from(String value) {
            String text = value == null ? "" : value.toLowerCase();
            if (text.contains("excellent")) {
                return new BadgeStyle(ReportBranding.GREEN_BG, ReportBranding.GREEN_TEXT, false);
            }
            if (text.contains("good")) {
                return new BadgeStyle(new Color(0xdb, 0xea, 0xfe), new Color(0x1e, 0x40, 0xaf), false);
            }
            if (text.contains("at risk")) {
                return new BadgeStyle(ReportBranding.YELLOW_BG, ReportBranding.YELLOW_TEXT, false);
            }
            if (text.contains("critical")) {
                return new BadgeStyle(ReportBranding.RED_BG, ReportBranding.RED_TEXT, true);
            }
            return new BadgeStyle(ReportBranding.LIGHT_BLUE_BG, ReportBranding.SLATE_700, false);
        }
    }

    private static class RoundedBadgeBackground implements PdfPCellEvent {
        private final Color badgeColor;
        private final Color rowColor;

        RoundedBadgeBackground(Color badgeColor, Color rowColor) {
            this.badgeColor = badgeColor;
            this.rowColor = rowColor;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            cb.saveState();
            cb.setColorFill(rowColor);
            cb.rectangle(position.getLeft(), position.getBottom(), position.getWidth(), position.getHeight());
            cb.fill();
            cb.setColorFill(badgeColor);
            float padX = 10f;
            float padY = 3f;
            cb.roundRectangle(position.getLeft() + padX, position.getBottom() + padY,
                    position.getWidth() - (2 * padX), position.getHeight() - (2 * padY), 8f);
            cb.fill();
            cb.restoreState();
        }
    }
}
