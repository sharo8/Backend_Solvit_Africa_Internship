package com.solvit.internship_system.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTableSection {
    private String sectionTitle;
    private List<String> headers;
    private List<List<String>> rows;
}
