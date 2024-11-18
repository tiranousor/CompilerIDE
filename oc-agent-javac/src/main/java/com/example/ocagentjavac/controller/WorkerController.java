package com.example.ocagentjavac.controller;

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
        String language = payload.get("language");
        String mainClassName = payload.get("mainClassName");

        if (!"java".equalsIgnoreCase(language)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Текущая поддержка только языка Java"));
        }

        Path projectDir = Paths.get("/tmp", projectId);
        String projectPrefix = "projects/" + projectId + "/";

        try {
            prepareProjectDirectory(projectDir);
            downloadProjectFiles(projectPrefix, projectDir);

            boolean isMavenProject = Files.exists(projectDir.resolve("pom.xml"));

            if (isMavenProject) {
                return handleMavenProject(projectDir, mainClassName, projectId);
            } else {
                return handleNonMavenProject(projectDir, mainClassName, projectId);
            }

        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
            logger.error("MinioException при компиляции проекта '{}': {}", projectId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            logger.error("IOException при компиляции проекта '{}': {}", projectId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException e) {
            logger.error("InterruptedException при компиляции проекта '{}': {}", projectId, e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(Map.of("error", "Процесс был прерван"));
        } catch (Exception e) {
            logger.error("Ошибка при компиляции проекта '{}': {}", projectId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка компиляции проекта"));
        } finally {
            FileSystemUtils.deleteRecursively(projectDir.toFile());
            logger.info("Временные файлы проекта '{}' успешно удалены.", projectId);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Подготавливает директорию проекта: удаляет существующую и создает новую.
     */
    private void prepareProjectDirectory(Path projectDir) throws IOException {
        FileSystemUtils.deleteRecursively(projectDir.toFile());
        Files.createDirectories(projectDir);
        logger.info("Директория проекта '{}' подготовлена.", projectDir.toString());
    }

    /**
     * Скачивает все файлы проекта из MinIO в локальную директорию.
     */
    private void downloadProjectFiles(String prefix, Path projectDir) throws MinioException, IOException, InvalidKeyException, NoSuchAlgorithmException {
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

            if (!item.isDir()) {
                String objectName = item.objectName();
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
                    logger.info("Файл '{}' скачан из MinIO в '{}'", objectName, filePath.toString());
                } catch (MinioException | IOException e) {
                    logger.error("Ошибка при скачивании файла '{}': {}", objectName, e.getMessage());
                }
            }
        }
    }

    /**
     * Обрабатывает Maven-проекты: компилирует и запускает их.
     */
    private ResponseEntity<?> handleMavenProject(Path projectDir, String mainClassName, String projectId) throws IOException, InterruptedException {
        // Компиляция Maven-проекта
        String compileOutput = executeCommand(Arrays.asList("mvn", "clean", "compile"), projectDir, 60);
        if (compileOutput == null) {
            return buildErrorResponse("Compilation timed out or failed", 1);
        }

        // Запуск Maven-проекта
        String runOutput = executeCommand(Arrays.asList("mvn", "exec:java"), projectDir, 30);
        if (runOutput == null) {
            return buildErrorResponse("Execution timed out or failed", 1);
        }

        return buildSuccessResponse(runOutput, "", 0);
    }

    /**
     * Обрабатывает не-Maven проекты: компилирует и запускает указанный класс.
     */
    private ResponseEntity<?> handleNonMavenProject(Path projectDir, String mainClassName, String projectId) throws IOException, InterruptedException {
        // Компиляция Java-файлов
        List<Path> javaFiles = collectJavaFiles(projectDir);
        if (javaFiles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Нет .java файлов для компиляции"));
        }

        List<String> javacCommand = new ArrayList<>();
        javacCommand.add("javac");
        for (Path javaFile : javaFiles) {
            javacCommand.add(javaFile.toString());
        }

        String compileOutput = executeCommand(javacCommand, projectDir, 60);
        if (compileOutput == null) {
            return buildErrorResponse("Compilation timed out or failed", 1);
        }

        // Запуск указанного класса
        System.out.println(mainClassName);
        List<String> javaRunCommand = Arrays.asList("java", "-cp", projectDir.toString(), mainClassName.replace(".java", ""));
        String runOutput = executeCommand(javaRunCommand, projectDir, 30);
        if (runOutput == null) {
            return buildErrorResponse("Execution timed out or failed", 1);
        }

        return buildSuccessResponse(runOutput, "", 0);
    }

    /**
     * Собирает все Java-файлы в проекте.
     */
    private List<Path> collectJavaFiles(Path projectDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(projectDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFiles::add);
        return javaFiles;
    }

    /**
     * Выполняет команду и возвращает её вывод. Возвращает null, если команда не завершилась в установленное время.
     */
    private String executeCommand(List<String> command, Path workingDir, long timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("Команда '{}' превысила время выполнения и была принудительно завершена.", String.join(" ", command));
                return null;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            logger.error("Команда '{}' завершилась с ошибкой: {}", String.join(" ", command), exitCode);
            return null;
        }

        return output.toString();
    }

    /**
     * Создаёт успешный ответ.
     */
    private ResponseEntity<?> buildSuccessResponse(String stdout, String stderr, int returnCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("stdout", stdout);
        response.put("stderr", stderr);
        response.put("returncode", returnCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Создаёт ошибочный ответ.
     */
    private ResponseEntity<?> buildErrorResponse(String stderr, int returnCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("stdout", "");
        response.put("stderr", stderr);
        response.put("returncode", returnCode);
        return ResponseEntity.status(500).body(response);
    }

}
