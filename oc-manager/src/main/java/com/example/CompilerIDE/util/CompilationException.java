package com.example.CompilerIDE.util;

import java.util.List;
import java.util.Map;
public class CompilationException extends RuntimeException {
    private String fileName;
    private int lineNumber;
    private int columnNumber;

    public CompilationException(String message, String fileName, int lineNumber, int columnNumber) {
        super(message);
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    // Геттеры
    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }
}
