package com.example.CompilerIDE.dto;

import java.util.List;
import java.util.Map;

public class CompilationResult {
    private String stdout;
    private List<Map<String, Object>> stderr;
    private int returnCode;

    // Геттеры и сеттеры

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public List<Map<String, Object>> getStderr() {
        return stderr;
    }

    public void setStderr(List<Map<String, Object>> stderr) {
        this.stderr = stderr;
    }

    public int getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(int returnCode) {
        this.returnCode = returnCode;
    }
}
