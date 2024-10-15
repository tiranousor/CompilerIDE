package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.CompileRequest;
import com.example.CompilerIDE.services.CodeCompilationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/compile")
public class CompilationController {

    private final CodeCompilationService codeCompilationService;

    @Autowired
    public CompilationController(CodeCompilationService codeCompilationService) {
        this.codeCompilationService = codeCompilationService;
    }

    @PostMapping
    @ResponseBody
    public String compileCode(@RequestBody CompileRequest request) {

        return codeCompilationService.compileCode(request);
    }
}
