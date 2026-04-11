package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "report_history", indexes = {
    @Index(columnList = "generated_by"),
    @Index(columnList = "generated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_type", nullable = false, length = 50)
    private String reportType;

    @Column(name = "period", length = 50)
    private String period;

    @Column(name = "reference_number", length = 48)
    private String referenceNumber;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Column(name = "generated_by")
    private Long generatedBy;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;
}
