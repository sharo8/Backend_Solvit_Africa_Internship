package com.solvit.internship_system.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorizontalBarChartSpec {
    private String title;
    @Builder.Default
    private Map<String, Double> values = new LinkedHashMap<>();
    /** Hex e.g. #2563eb */
    private String barHexColor;
    /** How to format numbers beside bars; default PERCENT for backward compatibility. */
    @Builder.Default
    private HorizontalBarValueMode valueMode = HorizontalBarValueMode.PERCENT;
}
