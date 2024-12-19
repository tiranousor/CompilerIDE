package com.example.CompilerIDE.dto;

import lombok.Getter;

@Getter
public class ContainerStatsDTO {
    private final String containerId;
    private final String name;
    private final double cpuUsagePercent;
    private final long memoryUsageBytes;
    private final long memoryLimitBytes;
    private final double memoryUsagePercent;

    public ContainerStatsDTO(String containerId, String name, double cpuUsagePercent, long memoryUsageBytes, long memoryLimitBytes) {
        this.containerId = containerId;
        this.name = name;
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsageBytes = memoryUsageBytes;
        this.memoryLimitBytes = memoryLimitBytes;
        this.memoryUsagePercent = memoryLimitBytes > 0 ? (memoryUsageBytes * 100.0 / memoryLimitBytes) : 0.0;
    }

}
