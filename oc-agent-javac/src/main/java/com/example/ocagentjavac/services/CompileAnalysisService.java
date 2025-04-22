package com.example.ocagentjavac.services;

import com.example.ocagentjavac.dto.DiagnosticResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompileAnalysisService {

    public List<DiagnosticResult> analyzeCompileErrors(String className, String code) {
        InMemoryJavaCompiler compiler = new InMemoryJavaCompiler();
        return compiler.compile(className, code);
    }
}