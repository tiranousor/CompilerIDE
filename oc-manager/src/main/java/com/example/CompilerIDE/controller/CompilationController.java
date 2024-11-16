package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.dto.CompileRequest;
import com.example.CompilerIDE.services.CodeCompilationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/compile")
public class CompilationController {

    private final CodeCompilationService codeCompilationService;

    @Autowired
    public CompilationController(CodeCompilationService codeCompilationService) {
        this.codeCompilationService = codeCompilationService;
    }

    @PostMapping
    public ResponseEntity<?> compileCode(@RequestBody CompileRequest request) {
        try {
            Map<String, Object> response = codeCompilationService.compileCode(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("stdout", "");
            errorResponse.put("stderr", Collections.singletonList(
                    Map.of(
                            "message", "Ошибка при компиляции: " + e.getMessage(),
                            "file", "",
                            "line", 0,
                            "column", 0
                    )
            ));
            errorResponse.put("returncode", 1);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

}
