package com.example.dispatcheranalyze.dto;

import lombok.Data;

import java.util.Map;

/**
 * Запрос на статический анализ.
 */
@Data
public class AnalyzeRequest {
    private Map<String,String> sources;
    private String current;
}