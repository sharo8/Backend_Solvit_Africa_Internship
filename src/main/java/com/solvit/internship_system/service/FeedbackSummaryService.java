package com.solvit.internship_system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.solvit.internship_system.report.ReportBranding;
import com.solvit.internship_system.dto.feedback.CriterionTrendPointDto;
import com.solvit.internship_system.dto.feedback.FeedbackSummaryDto;
import com.solvit.internship_system.entity.Feedback;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.FeedbackRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackSummaryService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final ObjectMapper objectMapper;

    public FeedbackSummaryDto generateSummary(Long internId, LocalDate from, LocalDate to, Long actorId) {
        User actor = userRepository.findById(actorId).orElseThrow(() -> new ResourceNotFoundException("User", actorId));
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        validateAccess(actor, internId);

        Instant fromI = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Feedback> all = feedbackRepository.findByIntern_IdAndCreatedAtBetweenOrderByCreatedAtAsc(internId, fromI, toI);

        Map<String, List<Double>> byCriterion = new LinkedHashMap<>();
        List<CriterionTrendPointDto> trend = new ArrayList<>();
        List<Double> overallSeries = new ArrayList<>();
        List<String> strengthsPool = new ArrayList<>();
        List<String> improvementsPool = new ArrayList<>();
        List<String> recommendationsPool = new ArrayList<>();

        for (Feedback f : all) {
            Map<String, Double> parsed = parseCriteriaScores(f.getStructuredRatings());
            if (!parsed.isEmpty()) {
                parsed.forEach((k, v) -> byCriterion.computeIfAbsent(k, kk -> new ArrayList<>()).add(v));
            }
            double overall = f.getRatingScore() != null ? f.getRatingScore() : (!parsed.isEmpty()
                    ? parsed.values().stream().mapToDouble(Double::doubleValue).average().orElse(0)
                    : 0);
            overallSeries.add(overall);
            trend.add(CriterionTrendPointDto.builder()
                    .date(f.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                    .criteria(parsed)
                    .overallScore(overall)
                    .build());
            extractTextPools(f.getStructuredRatings(), strengthsPool, improvementsPool, recommendationsPool);
        }

        Map<String, Double> criteriaAverages = byCriterion.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0),
                        (a, b) -> a, LinkedHashMap::new));

        double overallAverage = overallSeries.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double progressionPercent = 0;
        if (overallSeries.size() >= 2 && overallSeries.get(0) > 0) {
            progressionPercent = ((overallSeries.get(overallSeries.size() - 1) - overallSeries.get(0)) / overallSeries.get(0)) * 100.0;
        }

        return FeedbackSummaryDto.builder()
                .internId(internId)
                .internName(intern.getFirstName() + " " + intern.getLastName())
                .from(from)
                .to(to)
                .feedbackCount(all.size())
                .overallAverage(round2(overallAverage))
                .progressionPercent(round2(progressionPercent))
                .criteriaAverages(criteriaAverages.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> round2(e.getValue()), (a, b) -> a, LinkedHashMap::new)))
                .criteriaTrend(trend)
                .topStrengths(topKeywords(strengthsPool, 3))
                .topImprovements(topKeywords(improvementsPool, 3))
                .consolidatedRecommendations(topKeywords(recommendationsPool, 6))
                .build();
    }

    public byte[] exportSummaryPdf(FeedbackSummaryDto summary) {
        try {
            byte[] logoBytes = loadLogoBytes();
            Document document = new Document(PageSize.A4, 36, 36, 48, 36);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();

            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{1.2f, 3.8f});
            header.setSpacingAfter(16f);

            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setBackgroundColor(ReportBranding.DARK_NAVY);
            logoCell.setPadding(14);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Image logo = logoImage(logoBytes, 72f, 36f);
            if (logo != null) {
                logoCell.addElement(logo);
            } else {
                Font brandFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
                logoCell.addElement(new Phrase("SOLVIT", brandFont));
            }
            header.addCell(logoCell);

            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setBackgroundColor(ReportBranding.DARK_NAVY);
            titleCell.setPadding(14);
            titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE);
            Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, ReportBranding.LIGHT_TEXT);
            Paragraph titleBlock = new Paragraph();
            titleBlock.add(new Chunk("Feedback summary\n", titleFont));
            titleBlock.add(new Chunk("Internship performance overview", subFont));
            titleCell.addElement(titleBlock);
            header.addCell(titleCell);
            document.add(header);

            PdfPTable band = new PdfPTable(1);
            band.setWidthPercentage(100);
            PdfPCell bandCell = new PdfPCell();
            bandCell.setBorder(Rectangle.NO_BORDER);
            bandCell.setBackgroundColor(ReportBranding.BLUE);
            bandCell.setFixedHeight(6f);
            band.addCell(bandCell);
            document.add(band);

            Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, ReportBranding.SLATE_500);
            Font valueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, ReportBranding.SLATE_700);
            PdfPTable meta = new PdfPTable(2);
            meta.setWidthPercentage(100);
            meta.setSpacingAfter(14f);
            float[] metaWidths = {1.4f, 2.6f};
            meta.setWidths(metaWidths);
            addMetaRow(meta, "Intern", summary.internName(), labelFont, valueFont);
            addMetaRow(meta, "Period", summary.from() + " → " + summary.to(), labelFont, valueFont);
            addMetaRow(meta, "Feedback count", String.valueOf(summary.feedbackCount()), labelFont, valueFont);
            addMetaRow(meta, "Overall average", String.valueOf(summary.overallAverage()), labelFont, valueFont);
            addMetaRow(meta, "Progression", summary.progressionPercent() + "%", labelFont, valueFont);
            document.add(meta);

            Font sectionFont = new Font(Font.HELVETICA, 11, Font.BOLD, ReportBranding.DARK_NAVY);
            Paragraph critTitle = new Paragraph("Criteria averages", sectionFont);
            critTitle.setSpacingBefore(6f);
            critTitle.setSpacingAfter(8f);
            document.add(critTitle);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingAfter(14f);
            Font headFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            PdfPCell h1 = new PdfPCell(new Phrase("CRITERION", headFont));
            h1.setBackgroundColor(ReportBranding.DARK_NAVY);
            h1.setBorderColor(ReportBranding.BORDER_SLATE);
            h1.setBorderWidth(0.5f);
            h1.setPaddingTop(10);
            h1.setPaddingBottom(10);
            h1.setPaddingLeft(12);
            table.addCell(h1);
            PdfPCell h2 = new PdfPCell(new Phrase("AVERAGE", headFont));
            h2.setBackgroundColor(ReportBranding.DARK_NAVY);
            h2.setBorderColor(ReportBranding.BORDER_SLATE);
            h2.setBorderWidth(0.5f);
            h2.setPaddingTop(10);
            h2.setPaddingBottom(10);
            h2.setPaddingLeft(12);
            table.addCell(h2);

            Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL, ReportBranding.SLATE_700);
            int row = 0;
            if (summary.criteriaAverages() == null || summary.criteriaAverages().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No criterion scores in this period.", cellFont));
                empty.setColspan(2);
                empty.setBackgroundColor(ReportBranding.LIGHT_BLUE_BG);
                empty.setBorderColor(ReportBranding.BORDER_SLATE);
                empty.setPadding(12);
                table.addCell(empty);
            } else {
                for (Map.Entry<String, Double> e : summary.criteriaAverages().entrySet()) {
                    Color bg = (row % 2 == 0) ? Color.WHITE : ReportBranding.ROW_ALT;
                    row++;
                    PdfPCell c1 = new PdfPCell(new Phrase(formatCriterionLabel(e.getKey()), cellFont));
                    c1.setBackgroundColor(bg);
                    c1.setBorderColor(ReportBranding.BORDER_SLATE);
                    c1.setPadding(10);
                    table.addCell(c1);
                    PdfPCell c2 = new PdfPCell(new Phrase(String.valueOf(e.getValue()), cellFont));
                    c2.setBackgroundColor(bg);
                    c2.setBorderColor(ReportBranding.BORDER_SLATE);
                    c2.setPadding(10);
                    table.addCell(c2);
                }
            }
            document.add(table);

            Paragraph qualTitle = new Paragraph("Qualitative highlights", sectionFont);
            qualTitle.setSpacingBefore(4f);
            qualTitle.setSpacingAfter(8f);
            document.add(qualTitle);

            addQualBox(document, "Top strengths", String.join(", ", summary.topStrengths()));
            addQualBox(document, "Top improvements", String.join(", ", summary.topImprovements()));
            addQualBox(document, "Recommendations", String.join(", ", summary.consolidatedRecommendations()));

            Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, ReportBranding.SLATE_400);
            Paragraph footer = new Paragraph("SOLVIT AFRICA — Internship Management System | Confidential", footerFont);
            footer.setSpacingBefore(20f);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export summary PDF", e);
        }
    }

    private void addMetaRow(PdfPTable meta, String label, String value, Font labelFont, Font valueFont) throws DocumentException {
        PdfPCell l = new PdfPCell(new Phrase(label.toUpperCase(Locale.ROOT), labelFont));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingBottom(6);
        meta.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value != null ? value : "—", valueFont));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPaddingBottom(6);
        meta.addCell(v);
    }

    private void addQualBox(Document document, String title, String body) throws DocumentException {
        Font titleF = new Font(Font.HELVETICA, 9, Font.BOLD, ReportBranding.BLUE);
        Font bodyF = new Font(Font.HELVETICA, 10, Font.NORMAL, ReportBranding.SLATE_700);
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(8f);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(ReportBranding.BORDER_SLATE);
        cell.setBorderWidth(0.5f);
        cell.setBackgroundColor(ReportBranding.LIGHT_BLUE_BG);
        cell.setPadding(12);
        Paragraph p = new Paragraph();
        p.add(new Chunk(title + "\n", titleF));
        p.add(new Chunk(body != null && !body.isBlank() ? body : "—", bodyF));
        cell.addElement(p);
        box.addCell(cell);
        document.add(box);
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

    /**
     * Turns API keys like {@code technicalSkills} or {@code punctualityAttendance} into readable labels.
     */
    static String formatCriterionLabel(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String k = key.trim();
        Map<String, String> known = Map.ofEntries(
                Map.entry("technicalSkills", "Technical skills"),
                Map.entry("softSkills", "Soft skills"),
                Map.entry("communication", "Communication"),
                Map.entry("punctualityAttendance", "Punctuality & attendance"),
                Map.entry("initiativeProactivity", "Initiative & proactivity"),
                Map.entry("teamworkCollaboration", "Teamwork & collaboration"),
                Map.entry("problemSolving", "Problem solving"),
                Map.entry("adaptability", "Adaptability"),
                Map.entry("qualityOfWork", "Quality of work")
        );
        if (known.containsKey(k)) {
            return known.get(k);
        }
        String spaced = k.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        String[] parts = spaced.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private void validateAccess(User actor, Long internId) {
        if (actor.getRole() == Role.ADMIN || actor.getRole() == Role.HR) return;
        if (actor.getRole() == Role.INTERN && actor.getId().equals(internId)) return;
        if (actor.getRole() == Role.SUPERVISOR) {
            boolean ok = internProfileRepository.findByUser_Id(internId)
                    .map(p -> actor.getId().equals(p.getSupervisorUserId()))
                    .orElse(false);
            if (ok) return;
        }
        throw new AccessDeniedException("You can only access your own scoped data");
    }

    private Map<String, Double> parseCriteriaScores(String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) return Map.of();
        try {
            Map<String, Object> root = objectMapper.readValue(structuredJson, new TypeReference<>() {});
            Object criteriaObj = root.get("criteria");
            if (!(criteriaObj instanceof List<?> list)) return Map.of();
            Map<String, Double> out = new LinkedHashMap<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object key = m.get("key");
                    Object score = m.get("score");
                    if (key != null && score instanceof Number n) {
                        out.put(String.valueOf(key), n.doubleValue());
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void extractTextPools(String structuredJson, List<String> strengths, List<String> improvements, List<String> recommendations) {
        if (structuredJson == null || structuredJson.isBlank()) return;
        try {
            Map<String, Object> root = objectMapper.readValue(structuredJson, new TypeReference<>() {});
            addWords(strengths, String.valueOf(root.getOrDefault("strengths", "")));
            addWords(improvements, String.valueOf(root.getOrDefault("areasImprovement", "")));
            addWords(recommendations, String.valueOf(root.getOrDefault("recommendations", "")));
        } catch (Exception ignored) {
            // ignore malformed structured payload
        }
    }

    private void addWords(List<String> pool, String text) {
        if (text == null) return;
        for (String w : text.toLowerCase().split("[^a-zA-Z]+")) {
            if (w.length() >= 4) pool.add(w);
        }
    }

    private List<String> topKeywords(List<String> words, int limit) {
        return words.stream()
                .collect(Collectors.groupingBy(x -> x, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

