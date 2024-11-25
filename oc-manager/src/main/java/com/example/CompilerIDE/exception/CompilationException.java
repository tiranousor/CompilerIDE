package com.example.CompilerIDE.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Map;

@Getter
public class CompilationException extends RuntimeException {
    private String fileName;
    private int lineNumber;
    private int columnNumber;
    private String specificMessage;

    public CompilationException(String generalMessage) {
        super(generalMessage);
    }

    public CompilationException(String generalMessage, Map<String, Object> errorDetails) {
        super(generalMessage);
        this.specificMessage = (String) errorDetails.getOrDefault("message", "");
        this.fileName = (String) errorDetails.getOrDefault("file", "");
        this.lineNumber = errorDetails.getOrDefault("line", 0) instanceof Integer ? (Integer) errorDetails.getOrDefault("line", 0) : 0;
        this.columnNumber = errorDetails.getOrDefault("column", 0) instanceof Integer ? (Integer) errorDetails.getOrDefault("column", 0) : 0;
    }

}
