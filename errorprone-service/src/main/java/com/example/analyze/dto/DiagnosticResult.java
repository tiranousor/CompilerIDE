package com.example.analyze.dto;

import lombok.Data;

@Data
public class DiagnosticResult {
    private String file;
    private int line;
    private int column;
    private String message;
    private String fix;
}