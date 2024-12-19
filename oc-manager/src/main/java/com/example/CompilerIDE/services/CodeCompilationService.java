package com.example.CompilerIDE.services;

import com.example.CompilerIDE.dto.CompilationResult;
import com.example.CompilerIDE.dto.CompileRequest;
import com.example.CompilerIDE.client.CompileClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CodeCompilationService {

    private final CompileClient compileClient;

    @Autowired
    public CodeCompilationService(CompileClient compileClient) {
        this.compileClient = compileClient;
    }

    public CompilationResult compileCode(CompileRequest request) {
        ResponseEntity<Map<String, Object>> response = compileClient.compileCode(request);
        return mapToCompilationResult(response.getBody());
    }

    private CompilationResult mapToCompilationResult(Map<String, Object> responseBody) {
        CompilationResult result = new CompilationResult();

        Object stdoutObj = responseBody.getOrDefault("stdout", "");
        result.setStdout(stdoutObj != null ? stdoutObj.toString() : "");

        Object stderrObj = responseBody.get("stderr");
        if (stderrObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stderrList = (List<Map<String, Object>>) stderrObj;
            result.setStderr(stderrList);
        } else if (stderrObj instanceof String) {
            String stderrString = (String) stderrObj;
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("message", stderrString);
            errorMap.put("file", "");
            errorMap.put("line", 0);
            errorMap.put("column", 0);
            result.setStderr(Collections.singletonList(errorMap));
        } else {
            result.setStderr(Collections.emptyList());
        }

        Object returnCodeObj = responseBody.getOrDefault("returncode", 0);
        if (returnCodeObj instanceof Integer) {
            result.setReturnCode((Integer) returnCodeObj);
        } else {
            result.setReturnCode(0);
        }

        return result;
    }





}