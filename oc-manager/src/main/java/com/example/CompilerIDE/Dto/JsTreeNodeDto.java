package com.example.CompilerIDE.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsTreeNodeDto {
    private String id;              // Уникальный идентификатор узла
    private String text;            // Текст, отображаемый в jsTree
    private String type;            // "file" или "folder"
    private List<JsTreeNodeDto> children; // Дочерние узлы
    private String content;         // Содержимое файла (только для файлов)
}
