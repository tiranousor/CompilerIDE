package com.example.ocagentjavac.dto;
import lombok.Data;

@Data
public class DiagnosticResult {
    private String message;
    private String file;
    private long line;
    private long column;
}