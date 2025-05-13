// src/main/java/com/example/jdtls/QuickFixController.java
package com.example.jdtls.controller;

import com.example.jdtls.dto.QuickFixRequest;
import com.example.jdtls.services.QuickFixService;
import org.eclipse.lsp4j.CodeAction;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/quickfix")
public class QuickFixController {

    private final QuickFixService service;

    public QuickFixController(QuickFixService service) {
        this.service = service;
    }

    @PostMapping
    public CompletableFuture<List<CodeAction>> quickFix(@RequestBody QuickFixRequest req) {
        // req.uri — file://…; req.content — исходный текст Java
        return service.getQuickFixes(req.getUri(), req.getContent());
    }
}
