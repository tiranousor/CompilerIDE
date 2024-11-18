package com.example.CompilerIDE.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompileRequest {
    private String project_id;
    private String language;
    private String mainClassName;
}
