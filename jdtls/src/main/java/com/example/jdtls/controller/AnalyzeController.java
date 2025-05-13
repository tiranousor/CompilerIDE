// src/main/java/com/example/jdtls/controller/AnalyzeController.java
package com.example.jdtls.controller;

import com.example.jdtls.services.AnalyzeService;
import org.eclipse.lsp4j.Diagnostic;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/analyze")
public class AnalyzeController {

    private final AnalyzeService analyzeService;

    public AnalyzeController(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    @PostMapping
    public Map<String, List<Diagnostic>> analyze(@RequestBody Map<String, String> sources) {
        // sources: { "file:///…/Foo.java": "public class Foo { … }", … }
        return analyzeService.analyze(sources);
    }
}
