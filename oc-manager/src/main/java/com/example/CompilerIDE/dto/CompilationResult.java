package com.example.CompilerIDE.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CompilationResult {
    private String stdout;
    private List<Map<String, Object>> stderr;
    private int returnCode;
}
