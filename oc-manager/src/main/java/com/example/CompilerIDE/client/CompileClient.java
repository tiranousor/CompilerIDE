package com.example.CompilerIDE.client;

import com.example.CompilerIDE.config.CompileClientConfig;
import com.example.CompilerIDE.dto.CompileRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "dispatcher", url = "${dispatcher.url}", configuration = CompileClientConfig.class)
public interface CompileClient {
    @PostMapping(value = "/compile", consumes = "application/json")
    ResponseEntity<Map<String, Object>> compileCode(@RequestBody CompileRequest request);}

