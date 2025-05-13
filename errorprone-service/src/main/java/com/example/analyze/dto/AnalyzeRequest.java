package com.example.analyze.dto;

import java.util.Map;
public class AnalyzeRequest {
    private Map<String, String> sources;
    public Map<String, String> getSources() { return sources; }
    public void setSources(Map<String, String> s) { this.sources = s; }
}