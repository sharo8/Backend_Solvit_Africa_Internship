package com.solvit.internship_system.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportRequest {

    private String reportType;
    private String format;

    private Long internId;
    private Long supervisorId;
    private Long groupId;

    private LocalDate dateFrom;
    private LocalDate dateTo;

    /** DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUAL, CUSTOM (or null to infer from dates). */
    private String period;

    private Integer year;
    private Integer month;
    private Integer week;
    private Integer quarter;

    private List<Long> comparisonGroupIds;
}
