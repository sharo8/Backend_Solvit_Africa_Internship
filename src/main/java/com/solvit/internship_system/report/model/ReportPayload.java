package com.solvit.internship_system.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportPayload {
    private String pdfMainTitle;
    private String periodDescription;
    private String reference;
    private String generatedAtText;

    @Builder.Default
    private List<KpiEntry> kpis = new ArrayList<>();

    @Builder.Default
    private List<ReportTableSection> tables = new ArrayList<>();

    @Builder.Default
    private List<String> notes = new ArrayList<>();

    @Builder.Default
    private List<HorizontalBarChartSpec> horizontalBarCharts = new ArrayList<>();
}
