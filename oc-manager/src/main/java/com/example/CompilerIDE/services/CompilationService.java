package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.regex.*;
import java.util.ArrayList;
import java.util.HashMap;

@Service
public class CompilationService {

    public Map<String, Object> parseCompilationOutput(String stderr) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        String[] lines = stderr.split("\n");
        Pattern pattern = Pattern.compile("(.+\\.java):(\\d+): error: (.+)"); // Регулярное выражение для ошибок компилятора Java

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                Map<String, Object> error = new HashMap<>();
                error.put("file", matcher.group(1));
                error.put("line", Integer.parseInt(matcher.group(2)));
                error.put("message", matcher.group(3));
                error.put("column", 0); // Можно доработать, если информация о колонке доступна
                errors.add(error);
            }
        }

        response.put("stdout", "");
        response.put("stderr", errors);
        response.put("returncode", errors.isEmpty() ? 0 : 1);
        return response;
    }
}
