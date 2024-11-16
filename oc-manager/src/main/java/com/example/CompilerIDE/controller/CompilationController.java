package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.dto.CompilationResult;
import com.example.CompilerIDE.dto.CompileRequest;
import com.example.CompilerIDE.services.CodeCompilationService;
import com.example.CompilerIDE.util.CompilationException;
import org.slf4j.LoggerFactory;
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
            CompilationResult result = (CompilationResult) codeCompilationService.compileCode(request);
            if (result.getReturnCode() != 0) {
                // Если компиляция завершилась с ошибками
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // Логирование исключения для отладки
            LoggerFactory.getLogger(CompilationController.class).error("Ошибка при компиляции: ", e);

            // Возврат детализированной ошибки клиенту
            CompilationResult errorResult = new CompilationResult();
            errorResult.setStdout("");
            errorResult.setStderr(Collections.singletonList(
                    Map.of(
                            "message", "Внутренняя ошибка сервера: " + e.getMessage(),
                            "file", "",
                            "line", 0,
                            "column", 0
                    )
            ));
            errorResult.setReturnCode(1);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
}

