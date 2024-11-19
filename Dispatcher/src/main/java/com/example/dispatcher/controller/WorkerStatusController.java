package com.example.dispatcher.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/workers")
public class WorkerStatusController {

    private final Map<String, Boolean> javaWorkerStatus;
    private final Map<String, Boolean> pythonWorkerStatus;

    public WorkerStatusController(Map<String, Boolean> javaWorkerStatus, Map<String, Boolean> pythonWorkerStatus) {
        this.javaWorkerStatus = javaWorkerStatus;
        this.pythonWorkerStatus = pythonWorkerStatus;
    }

    @GetMapping("/status")
    public Map<String, Boolean> getWorkerStatus() {
        Map<String, Boolean> combinedStatus = new HashMap<>();
        combinedStatus.putAll(javaWorkerStatus);
        combinedStatus.putAll(pythonWorkerStatus);
        return combinedStatus;
    }
}
