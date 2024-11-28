package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
@Table(name = "daily_stats")
public class DailyStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "user_count", nullable = false)
    private Long userCount;

    @Column(name = "project_count", nullable = false)
    private Long projectCount;
}
