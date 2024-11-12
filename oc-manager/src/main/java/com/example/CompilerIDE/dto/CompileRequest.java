package com.example.CompilerIDE.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompileRequest {
    private List<FileData> files;
    private String language;
}
