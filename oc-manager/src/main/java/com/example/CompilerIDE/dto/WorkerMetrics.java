package com.example.CompilerIDE.dto;

import lombok.Data;

@Data
public class WorkerMetrics {
    private String url;
    private boolean available;
    private double cpuUsage;
    private double memoryUsage;
}
