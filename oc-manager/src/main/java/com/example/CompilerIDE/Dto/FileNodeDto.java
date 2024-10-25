package com.example.CompilerIDE.Dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileNodeDto {
    private String path;
    private String content; // Может быть null для папок
    private String type;    // "folder" для папок, может быть null для файлов
    private List<FileNodeDto> files;
}
