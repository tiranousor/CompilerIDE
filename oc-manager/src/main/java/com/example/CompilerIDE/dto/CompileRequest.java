package com.example.CompilerIDE.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompileRequest {
    private String project_id;
    private String language;
    private String code;
    private String filename;
    private String mainClassName;
}
