// src/main/java/com/example/analyzeworker/controller/AnalyzeWorkerController.java
package com.example.ocagentanalyzejava.controller;

import com.example.ocagentanalyzejava.dto.AnalyzeRequest;
import com.example.ocagentanalyzejava.dto.DiagnosticResult;
import com.example.ocagentanalyzejava.service.StaticAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analyze")
public class AnalyzeWorkerController {

    private final StaticAnalysisService analysisService;

    public AnalyzeWorkerController(StaticAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ResponseEntity<List<DiagnosticResult>> analyze(@RequestBody AnalyzeRequest req) {
        List<DiagnosticResult> result = analysisService.analyze(req.getSources(), req.getCurrent());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ANALYZE worker OK");
    }
}
