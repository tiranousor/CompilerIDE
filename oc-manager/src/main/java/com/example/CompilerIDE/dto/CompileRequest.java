package com.example.CompilerIDE.dto;

import lombok.Data;

@Data
public class CompileRequest {
    private String project_id;
    private String language;
    private String code;
    private String filename;
}
