package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.dto.CompilationResult;
import com.example.CompilerIDE.dto.CompileRequest;
import com.example.CompilerIDE.services.CodeCompilationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/compile")
public class CompilationController {

    private final CodeCompilationService codeCompilationService;

    @Autowired
    public CompilationController(CodeCompilationService codeCompilationService) {
        this.codeCompilationService = codeCompilationService;
    }

    @PostMapping
    public ResponseEntity<CompilationResult> compileCode(@RequestBody CompileRequest request) {
        CompilationResult result = codeCompilationService.compileCode(request);
        if (result.getReturnCode() != 0) {
            // Если компиляция завершилась с ошибками
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
