package com.example.analyze.controller;

// ErrorProneController.java
import com.example.analyze.dto.*;
import com.example.analyze.service.ErrorProneService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/errorprone")
public class ErrorProneController {
    private final ErrorProneService svc;
    public ErrorProneController(ErrorProneService svc) { this.svc = svc; }

    @PostMapping
    public List<DiagnosticResult> analyze(@RequestBody AnalyzeRequest req) {
        return svc.analyze(req.getSources());
    }

    @GetMapping("/health")
    public String health() { return "ERROR_PRONE OK"; }
}
