package com.example.CompilerIDE.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkerMetrics {
    private String url;

    private double cpuUsage;
    private double memoryUsage;
    private boolean available;
}
