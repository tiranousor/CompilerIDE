package com.example.ocagentpython.controller;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
public class WorkerController {

    private MinioClient minioClient;
    private static final Logger logger = LoggerFactory.getLogger(WorkerController.class);

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${MINIO_ENDPOINT:http://minio:9000}")
    private String minioEndpoint;

    @Value("${MINIO_ACCESS_KEY}")
    private String minioAccessKey;

    @Value("${MINIO_SECRET_KEY}")
    private String minioSecretKey;

    @PostConstruct
    public void init() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        this.minioClient = MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();

        logger.info("MinioClient успешно инициализирован.");
    }

    @PostMapping(value = "/compile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> compileProject(@RequestBody Map<String, String> payload) {
        String projectId = payload.get("project_id");
        String mainScriptName = payload.get("mainClassName"); // Используем для Python

        Path projectDir = Paths.get("/tmp", projectId);
        String projectPrefix = "projects/" + projectId + "/";

        try {
            prepareProjectDirectory(projectDir);
            downloadProjectFiles(projectPrefix, projectDir);

            if (!isValidMainScript(mainScriptName, projectDir)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Скрипт " + mainScriptName + " не найден или некорректен"));
            }

            // Запуск основного скрипта Python
            return executePythonScript(projectDir, mainScriptName);

        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
            logger.error("MinioException при обработке проекта '{}': {}", projectId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            logger.error("IOException при обработке проекта '{}': {}", projectId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException e) {
            logger.error("InterruptedException при обработке проекта '{}': {}", projectId, e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(Map.of("error", "Процесс был прерван"));
        } catch (Exception e) {
            logger.error("Ошибка при обработке проекта '{}': {}", projectId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка обработки проекта"));
        } finally {
            FileSystemUtils.deleteRecursively(projectDir.toFile());
            logger.info("Временные файлы проекта '{}' успешно удалены.", projectId);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void prepareProjectDirectory(Path projectDir) throws IOException {
        FileSystemUtils.deleteRecursively(projectDir.toFile());
        Files.createDirectories(projectDir);
        logger.info("Директория проекта '{}' подготовлена.", projectDir.toString());
    }

    private void downloadProjectFiles(String prefix, Path projectDir) throws MinioException, IOException, InvalidKeyException, NoSuchAlgorithmException {
        logger.info("Начинается загрузка файлов проекта с префиксом '{}'", prefix);
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(true)
                        .build()
        );

        for (Result<Item> result : results) {
            Item item;
            try {
                item = result.get();
            } catch (Exception e) {
                logger.error("Ошибка при получении объекта из MinIO: {}", e.getMessage());
                continue;
            }

            String objectName = item.objectName();
            if (objectName.endsWith("/")) {
                // Это директория, пропускаем её
                logger.info("Пропуск директории: {}", objectName);
                continue;
            }

            String relativePath = objectName.substring(prefix.length());
            Path filePath = projectDir.resolve(relativePath);
            Files.createDirectories(filePath.getParent());

            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            )) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Файл '{}' скачан из MinIO в '{}'", objectName, filePath);
            } catch (MinioException | IOException e) {
                logger.error("Ошибка при скачивании файла '{}': {}", objectName, e.getMessage());
            }
        }

        // Логирование всех файлов после загрузки
        try {
            Files.walk(projectDir)
                    .forEach(path -> logger.info("Файл в проекте: {}", path));
        } catch (IOException e) {
            logger.error("Ошибка при перечислении файлов: {}", e.getMessage());
        }
    }

    private boolean isValidMainScript(String mainScriptName, Path projectDir) throws IOException {
        if (mainScriptName == null || mainScriptName.trim().isEmpty()) {
            return false;
        }
        String scriptPath = mainScriptName;
        Path scriptFilePath = projectDir.resolve(scriptPath);
        return Files.exists(scriptFilePath) && Files.isRegularFile(scriptFilePath);
    }

    private ResponseEntity<?> executePythonScript(Path projectDir, String mainScriptName) throws IOException, InterruptedException {
        Path mainScriptPath = projectDir.resolve(mainScriptName);
        logger.info("Запуск скрипта: python {}", mainScriptPath.toString());

        ProcessBuilder processBuilder = new ProcessBuilder("python3", mainScriptPath.toString());
        processBuilder.directory(projectDir.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            logger.error("Запуск скрипта '{}' превысил время выполнения и был принудительно завершен.", mainScriptName);
            return ResponseEntity.status(500).body(Map.of(
                    "stdout", "",
                    "stderr", "Время выполнения скрипта истекло.",
                    "returncode", 1
            ));
        }

        int exitCode = process.exitValue();
        logger.info("Скрипт завершился с кодом {}", exitCode);

        return ResponseEntity.ok(Map.of(
                "stdout", output.toString(),
                "stderr", error.toString(),
                "returncode", exitCode
        ));
    }

}
