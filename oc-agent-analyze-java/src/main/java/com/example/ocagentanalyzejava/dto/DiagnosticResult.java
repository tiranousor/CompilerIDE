// src/main/java/com/example/analyzeworker/dto/DiagnosticResult.java
package com.example.ocagentanalyzejava.dto;

import lombok.Data;

@Data
public class DiagnosticResult {
    private String message;
    private String file;
    private long line;
    private long column;
    private int severity;
    private int endColumn;
}
