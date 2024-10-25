package com.example.CompilerIDE.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsTreeNodeDto {
    private String id;
    private String text;
    private String type;
    private List<JsTreeNodeDto> children;
    private Map<String, Object> data; // Добавлено поле data
}
