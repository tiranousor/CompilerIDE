package com.example.CompilerIDE.services;
import com.example.CompilerIDE.dto.CompileRequest;
import com.example.CompilerIDE.client.CompileClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CodeCompilationService {

    private final CompileClient compileClient;

    @Autowired
    public CodeCompilationService(CompileClient compileClient) {
        this.compileClient = compileClient;
    }

//    public String compileCode(CompileRequest request) {
//        System.out.println();
//        return compileClient.compileCode(request);
//    }
public Map<String, Object> compileCode(CompileRequest request) {
    ResponseEntity<Map<String, Object>> response = compileClient.compileCode(request);
    if (response.getStatusCode().is2xxSuccessful()) {
        return response.getBody();
    } else {
        // Обработка ошибок, если нужно
        throw new RuntimeException("Ошибка при компиляции кода");
    }
}
}