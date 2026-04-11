package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(name = "check_in_start_time")
    private LocalTime checkInStartTime;

    @Column(name = "check_in_deadline")
    private LocalTime checkInDeadline;

    @Column(name = "check_out_deadline")
    private LocalTime checkOutDeadline;

    @Column(name = "expected_hours_per_day")
    private Integer expectedHoursPerDay;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
