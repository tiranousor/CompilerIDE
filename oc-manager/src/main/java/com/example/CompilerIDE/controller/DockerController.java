package com.example.CompilerIDE.controller;
import com.example.CompilerIDE.dto.ContainerStatsDTO;
import com.example.CompilerIDE.services.WorkerStatusService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DockerController {
    private final WorkerStatusService workerStatusService;

    public DockerController(WorkerStatusService workerStatusService) {
        this.workerStatusService = workerStatusService;
    }

    @GetMapping("/containers/stats")
    public List<ContainerStatsDTO> getContainersStats() {
        return workerStatusService.getAllContainerStats();
    }
    @GetMapping("/system/memory")
    public MemoryResponse getSystemMemory() {
        WorkerStatusService.SystemMemoryInfo info = workerStatusService.getSystemMemoryInfo();
        return new MemoryResponse(info.getTotalMemory(), info.getUsedMemory());
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemoryResponse {
        private long totalMemory;
        private long usedMemory;

        public double getUsedPercent() {
            return totalMemory > 0 ? (usedMemory * 100.0 / totalMemory) : 0.0;
        }
    }
}
