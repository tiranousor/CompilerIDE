package com.example.CompilerIDE.util;

import java.util.List;
import java.util.Map;

public class CompilationException extends RuntimeException {
    private final List<Map<String, Object>> errors;

    public CompilationException(String message, List<Map<String, Object>> errors) {
        super(message);
        this.errors = errors;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }
}
