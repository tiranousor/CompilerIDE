package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.client.CompileClient;
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
    private CompileClient compileClient;
    @Autowired
    public CompilationController(CodeCompilationService codeCompilationService) {
        this.codeCompilationService = codeCompilationService;
    }

//    @PostMapping
//    @ResponseBody
//    public String compileCode(@RequestBody CompileRequest request) {
//
//        return codeCompilationService.compileCode(request);
//    }
@PostMapping
public ResponseEntity<?> compileCode(@RequestBody CompileRequest request) {
    try {
        Map<String, Object> response = codeCompilationService.compileCode(request);
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return ResponseEntity.status(500).body("Ошибка при компиляции");
    }
}
}
