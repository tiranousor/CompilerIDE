package com.example.CompilerIDE.services;

import com.example.CompilerIDE.dto.CompilationResult;
import com.example.CompilerIDE.dto.CompileRequest;
import com.example.CompilerIDE.client.CompileClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
        if (stdoutObj instanceof String) {
            result.setStdout((String) stdoutObj);
        } else {
            result.setStdout("");
        }

        // Обработка stderr
        Object stderrObj = responseBody.getOrDefault("stderr", Collections.emptyList());
        if (stderrObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stderrList = (List<Map<String, Object>>) stderrObj;
            result.setStderr(stderrList);
        } else if (stderrObj instanceof String) {
            result.setStderr(Collections.singletonList(
                    Map.of("message", stderrObj)
            ));
        } else {
            result.setStderr(Collections.emptyList());
        }

        // Обработка returncode (на стороне агентов используется именно такое имя)
        Object returnCodeObj = responseBody.get("returnCode");
        if (returnCodeObj == null) {
            returnCodeObj = responseBody.getOrDefault("returncode", 0);
        }
        if (returnCodeObj instanceof Integer) {
            result.setReturnCode((Integer) returnCodeObj);
        } else if (returnCodeObj instanceof String) {
            try {
                result.setReturnCode(Integer.parseInt((String) returnCodeObj));
            } catch (NumberFormatException e) {
                result.setReturnCode(0);
            }
        } else {
            result.setReturnCode(0);
        }

        return result;
    }
}
