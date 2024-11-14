package com.example.CompilerIDE.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileNodeDto {
    private String path;
    private String content;
    private String type;
    private List<FileNodeDto> files;
}
