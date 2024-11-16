package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.util.CompilationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

@RestController
@RequestMapping("/worker")
public class WorkerController {

    private static final Logger logger = LoggerFactory.getLogger(WorkerController.class);

    @PostMapping("/compile")
    public ResponseEntity<?> compileCode(@RequestBody Map<String, String> payload) {
        String projectId = payload.get("project_id");
        String language = payload.get("language");
        // Дополнительные параметры, если необходимо

        Path projectDir = Paths.get("/path/to/projects/", projectId); // Замените на реальный путь

        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            logger.error("Директория проекта не найдена: {}", projectDir.toString());
            Map<String, Object> response = new HashMap<>();
            response.put("stdout", "");
            response.put("stderr", Collections.singletonList(
                    Map.of(
                            "message", "Директория проекта не найдена.",
                            "file", "",
                            "line", 0,
                            "column", 0
                    )
            ));
            response.put("returncode", 1);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        try {
            if (language.equalsIgnoreCase("java")) {
                Path mainFile = projectDir.resolve("Main.java");
                if (!Files.exists(mainFile)) {
                    logger.error("Файл Main.java не найден в проекте.");
                    Map<String, Object> response = new HashMap<>();
                    response.put("stdout", "");
                    response.put("stderr", Collections.singletonList(
                            Map.of(
                                    "message", "Файл Main.java не найден.",
                                    "file", "Main.java",
                                    "line", 0,
                                    "column", 0
                            )
                    ));
                    response.put("returncode", 1);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }

                // Команда компиляции
                ProcessBuilder processBuilder = new ProcessBuilder("javac", "Main.java");
                processBuilder.directory(projectDir.toFile());
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();
                StringBuilder outputBuilder = new StringBuilder();
                List<Map<String, Object>> errors = new ArrayList<>();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    Pattern errorPattern = Pattern.compile("(\\S+\\.java):(\\d+):(\\d+):\\s+(error|warning):\\s+(.*)");
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append("\n");
                        Matcher matcher = errorPattern.matcher(line);
                        if (matcher.matches()) {
                            String file = matcher.group(1);
                            int lineNumber = Integer.parseInt(matcher.group(2));
                            int column = Integer.parseInt(matcher.group(3));
                            String type = matcher.group(4);
                            String message = matcher.group(5);
                            Map<String, Object> error = new HashMap<>();
                            error.put("message", message);
                            error.put("file", file);
                            error.put("line", lineNumber);
                            error.put("column", column);
                            errors.add(error);
                        }
                    }
                }

                int exitCode = process.waitFor();

                Map<String, Object> response = new HashMap<>();
                response.put("stdout", outputBuilder.toString());
                response.put("returncode", exitCode);
                response.put("stderr", errors);

                logger.info("Компиляция завершена с кодом: {}", exitCode);
                if (!errors.isEmpty()) {
                    logger.warn("Найдены ошибки компиляции: {}", errors.size());
                }

                if (exitCode != 0) {
                    throw new CompilationException("Компиляция не удалась.", "Main.java", 0, 0);
                }

                return ResponseEntity.ok(response);
            } else {
                logger.error("Не поддерживаемый язык: {}", language);
                Map<String, Object> response = new HashMap<>();
                response.put("stdout", "");
                response.put("stderr", Collections.singletonList(
                        Map.of(
                                "message", "Не поддерживаемый язык: " + language,
                                "file", "",
                                "line", 0,
                                "column", 0
                        )
                ));
                response.put("returncode", 1);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Ошибка при компиляции: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("stdout", "");
            response.put("stderr", Collections.singletonList(
                    Map.of(
                            "message", "Ошибка при компиляции: " + e.getMessage(),
                            "file", "",
                            "line", 0,
                            "column", 0
                    )
            ));
            response.put("returncode", 1);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (CompilationException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("stdout", "");
            response.put("stderr", Collections.singletonList(
                    Map.of(
                            "message", e.getMessage(),
                            "file", e.getFileName(),
                            "line", e.getLineNumber(),
                            "column", e.getColumnNumber()
                    )
            ));
            response.put("returncode", 1);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
