// src/main/java/com/example/analyzeworker/dto/AnalyzeRequest.java
package com.example.ocagentanalyzejava.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AnalyzeRequest {
    private Map<String,String> sources;
    private String current;
}

