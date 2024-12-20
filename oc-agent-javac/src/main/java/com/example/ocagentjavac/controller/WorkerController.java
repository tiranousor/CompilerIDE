package com.example.ocagentjavac.controller;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import lombok.AllArgsConstructor;
import lombok.Data;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

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
        String mainClassName = payload.get("mainClassName");
        logger.info("Получен запрос на компиляцию проекта '{}', главный класс: '{}'", projectId, mainClassName);

        Path projectDir = Paths.get("/tmp", projectId);
        String projectPrefix = "projects/" + projectId + "/";

        try {
            prepareProjectDirectory(projectDir);
            downloadProjectFiles(projectPrefix, projectDir);

            if (!isValidMainClass(mainClassName, projectDir)) {
                logger.warn("Класс '{}' не найден или некорректен в проекте '{}'", mainClassName, projectId);
                return ResponseEntity.badRequest().body(Map.of("error", "Класс " + mainClassName + " не найден или некорректен"));
            }

            boolean isMavenProject = Files.exists(projectDir.resolve("pom.xml"));
            logger.info("Проект '{}' является Maven-проектом: {}", projectId, isMavenProject);

            if (isMavenProject) {
                return handleMavenProject(projectDir, mainClassName);
            } else {
                return handleNonMavenProject(projectDir, mainClassName);
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

    private void prepareProjectDirectory(Path projectDir) throws IOException {
        logger.info("Подготовка директории проекта '{}'", projectDir.toString());
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

    }

    private boolean isValidMainClass(String mainClassName, Path projectDir) throws IOException {
        if (mainClassName == null || mainClassName.trim().isEmpty()) {
            return false;
        }

        if (mainClassName.endsWith(".java")) {
            mainClassName = mainClassName.substring(0, mainClassName.length() - 5);
        }

        String classRelativePath = mainClassName.replace('.', '/') + ".java";

        boolean isMavenProject = Files.exists(projectDir.resolve("pom.xml"));

        Path classFilePath;
        if (isMavenProject) {
            classFilePath = projectDir.resolve("src/main/java").resolve(classRelativePath);
        } else {
            classFilePath = projectDir.resolve(classRelativePath);
        }

        boolean exists = Files.exists(classFilePath);
        logger.info("Проверка существования класса '{}': {}", classFilePath.toString(), exists);
        return exists;
    }

    private ResponseEntity<?> handleMavenProject(Path projectDir, String mainClassName) throws IOException, InterruptedException {
        Path mavenRepo = Paths.get("/tmp/.m2/repository");
        Files.createDirectories(mavenRepo);
        logger.info("Используется локальный Maven репозиторий: '{}'", mavenRepo.toString());

        Path pomPath = projectDir.resolve("pom.xml");
        Map<String, String> mavenCoordinates = extractMavenCoordinates(pomPath);

        if (!mavenCoordinates.containsKey("groupId") || !mavenCoordinates.containsKey("artifactId")) {
            logger.error("Не удалось извлечь groupId или artifactId из pom.xml");
            return ResponseEntity.status(500).body(Map.of("error", "Не удалось извлечь groupId или artifactId из pom.xml"));
        }

        String projectGroupId = mavenCoordinates.get("groupId");
        String projectArtifactId = mavenCoordinates.get("artifactId");

        List<String> mvnCommands = Arrays.asList(
                "mvn",
                "-B",
                "-Dstyle.color=never",
                "-DtrimStackTrace=true",
                "clean",
                "compile",
                "dependency:copy-dependencies"
        );

        logger.info("Выполнение Maven команд: {}", String.join(" ", mvnCommands));
        CommandResult compileResult = executeCommand(mvnCommands, projectDir, 120);

        logger.info("Команда Maven завершилась с кодом: {}", compileResult.getReturnCode());

        logger.info("Maven stdout:\n{}", compileResult.getStdout());
        logger.info("Maven stderr:\n{}", compileResult.getStderr());

        if (compileResult.getReturnCode() != 0) {
            logger.warn("Ошибка компиляции Maven проекта '{}'", projectDir.toString());

            List<Map<String, Object>> errors = parseJavacErrors(compileResult.getStderr(), projectDir, projectGroupId, projectArtifactId);

            if (errors.isEmpty()) {
                errors = parseJavacErrors(compileResult.getStdout(), projectDir, projectGroupId, projectArtifactId);
            }

            logger.debug("Ошибки компиляции Maven проекта: {}", errors);
            return buildErrorResponse("", errors, compileResult.getReturnCode());
        }

        List<String> javaRunCommand = Arrays.asList("java", "-cp", "target/classes" + File.pathSeparator + "target/dependency/*", mainClassName);
        logger.info("Выполнение Java команды: {}", String.join(" ", javaRunCommand));
        CommandResult runResult = executeCommand(javaRunCommand, projectDir, 30);
        logger.info("Команда Java завершилась с кодом: {}", runResult.getReturnCode());

        if (runResult.getReturnCode() != 0) {
            logger.warn("Ошибка выполнения Java приложения в проекте '{}'", projectDir.toString());
            List<Map<String, Object>> errors = parseJavacErrors(runResult.getStderr(), projectDir, projectGroupId, projectArtifactId);
            logger.debug("Ошибки выполнения Java приложения: {}", errors);
            return buildErrorResponse(runResult.getStdout(), errors, runResult.getReturnCode());
        }

        logger.info("Проект '{}' успешно скомпилирован и выполнен.", projectDir.toString());
        return buildSuccessResponse(runResult.getStdout(), runResult.getStderr());
    }

    private ResponseEntity<?> handleNonMavenProject(Path projectDir, String mainClassName) throws IOException, InterruptedException {
        List<Path> javaFiles = collectJavaFiles(projectDir);
        logger.info("Найдено {} Java файлов для компиляции в проекте '{}'", javaFiles.size(), projectDir.toString());

        List<String> javacCommand = new ArrayList<>();
        javacCommand.add("javac");
        for (Path javaFile : javaFiles) {
            javacCommand.add(javaFile.toString());
        }

        logger.info("Выполнение команды javac: {}", String.join(" ", javacCommand));
        CommandResult compileResult = executeCommand(javacCommand, projectDir, 60);
        logger.info("Команда javac завершилась с кодом: {}", compileResult.getReturnCode());

        if (compileResult.getReturnCode() != 0) {
            logger.warn("Ошибка компиляции проекта '{}'", projectDir.toString());
            List<Map<String, Object>> errors = parseJavacErrors(compileResult.getStderr(), projectDir, null, null);
            logger.debug("Ошибки компиляции проекта: {}", errors);
            return buildErrorResponse(compileResult.getStdout(), errors, compileResult.getReturnCode());
        }

        List<String> javaRunCommand = Arrays.asList("java", "-cp", ".", mainClassName);
        logger.info("Выполнение Java команды: {}", String.join(" ", javaRunCommand));
        CommandResult runResult = executeCommand(javaRunCommand, projectDir, 30);
        logger.info("Команда Java завершилась с кодом: {}", runResult.getReturnCode());

        if (runResult.getReturnCode() != 0) {
            logger.warn("Ошибка выполнения Java приложения в проекте '{}'", projectDir.toString());
            List<Map<String, Object>> errors = parseJavacErrors(runResult.getStderr(), projectDir, null, null);
            if (runResult.getReturnCode() != 0 && errors.isEmpty()) {
                String runStderr = runResult.getStderr() == null ? "" : runResult.getStderr().toLowerCase();
                String runStdout = runResult.getStdout() == null ? "" : runResult.getStdout().toLowerCase();
                if (runStderr.contains("could not find or load main class") || runStdout.contains("could not find or load main class")) {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("message", "Не удалось запустить главный класс. Убедитесь, что 'package' соответствует структуре каталогов.");
                    errorMap.put("file", "");
                    errorMap.put("line", 0);
                    errorMap.put("column", 0);
                    errors.add(errorMap);
                } else {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("message", "Неизвестная ошибка при запуске приложения.");
                    errorMap.put("file", "");
                    errorMap.put("line", 0);
                    errorMap.put("column", 0);
                    errors.add(errorMap);
                }
                return buildErrorResponse(runResult.getStdout(), errors, runResult.getReturnCode());
            }

            logger.debug("Ошибки выполнения Java приложения: {}", errors);
            return buildErrorResponse(runResult.getStdout(), errors, runResult.getReturnCode());
        }

        logger.info("Проект '{}' успешно скомпилирован и выполнен.", projectDir.toString());
        return buildSuccessResponse(runResult.getStdout(), runResult.getStderr());
    }

    private List<Path> collectJavaFiles(Path projectDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(projectDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFiles::add);
        return javaFiles;
    }

    private CommandResult executeCommand(List<String> command, Path workingDir, long timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir.toFile());
        processBuilder.redirectErrorStream(false);

        processBuilder.environment().put("MAVEN_OPTS", "-Dmaven.repo.local=/tmp/.m2/repository");

        Files.createDirectories(Paths.get("/tmp/.m2/repository"));

        logger.info("Запуск процесса: {}", String.join(" ", command));
        Process process = processBuilder.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            } catch (IOException e) {
                logger.error("Ошибка при чтении stdout: {}", e.getMessage());
            }
        });

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            } catch (IOException e) {
                logger.error("Ошибка при чтении stderr: {}", e.getMessage());
            }
        });

        stdoutThread.start();
        stderrThread.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        stdoutThread.join(1000);
        stderrThread.join(1000);

        if (!finished) {
            process.destroyForcibly();
            logger.error("Команда '{}' превысила время выполнения и была принудительно завершена.", String.join(" ", command));
            return new CommandResult(null, "Время выполнения команды истекло.", 1);
        }

        int exitCode = process.exitValue();
        logger.info("Процесс завершился с кодом: {}", exitCode);

        return new CommandResult(stdout.toString(), stderr.toString(), exitCode);
    }

    private List<Map<String, Object>> parseJavacErrors(String stderrOutput, Path projectDir, String groupId, String artifactId) {
        List<Map<String, Object>> errorList = new ArrayList<>();
        Set<String> uniqueErrors = new HashSet<>();
        boolean collectArtifacts = false;

        if (stderrOutput == null || stderrOutput.isEmpty()) {
            logger.debug("Нет ошибок для парсинга.");
            return errorList;
        }

        String[] lines = stderrOutput.split("\n");
        Pattern artifactPattern = Pattern.compile("([a-zA-Z0-9_.\\-]+):([a-zA-Z0-9_.\\-]+):jar:([a-zA-Z0-9_.\\-]+)");

        logger.debug("Начинается парсинг ошибок компиляции. Входные данные:\n{}", stderrOutput);

        for (String line : lines) {
            line = line.trim();

            if (line.contains("The following artifacts could not be resolved:")) {
                collectArtifacts = true;

                String[] parts = line.split("The following artifacts could not be resolved:");
                if (parts.length > 1) {
                    String artifactsPart = parts[1].trim();
                    Matcher artifactMatcher = artifactPattern.matcher(artifactsPart);
                    while (artifactMatcher.find()) {
                        String currentGroupId = artifactMatcher.group(1);
                        String currentArtifactId = artifactMatcher.group(2);
                        String version = artifactMatcher.group(3);

                        if (groupId != null && artifactId != null &&
                                currentGroupId.equals(groupId) && currentArtifactId.equals(artifactId)) {
                            logger.debug("Исключение основной цели проекта из списка ошибок: {}:{}:{}", currentGroupId, currentArtifactId, version);
                            continue;
                        }

                        Map<String, Object> errorMap = new HashMap<>();
                        errorMap.put("message", "Не удалось разрешить зависимость: " + currentGroupId + ":" + currentArtifactId + ":" + version);
                        errorMap.put("file", "");
                        errorMap.put("line", 0);
                        errorMap.put("column", 0);

                        String key = currentGroupId + ":" + currentArtifactId + ":" + version;
                        logger.debug("Обнаружена ошибка Maven зависимости: {}", key);

                        if (!uniqueErrors.contains(key)) {
                            errorList.add(errorMap);
                            uniqueErrors.add(key);
                            logger.debug("Добавлена новая Maven ошибка: {}", key);
                        } else {
                            logger.debug("Пропущена дублирующая Maven ошибка: {}", key);
                        }
                    }
                }
                continue;
            }

            if (collectArtifacts) {
                Matcher artifactMatcher = artifactPattern.matcher(line);
                if (artifactMatcher.find()) {
                    String currentGroupId = artifactMatcher.group(1);
                    String currentArtifactId = artifactMatcher.group(2);
                    String version = artifactMatcher.group(3);

                    if (groupId != null && artifactId != null &&
                            currentGroupId.equals(groupId) && currentArtifactId.equals(artifactId)) {
                        logger.debug("Исключение основной цели проекта из списка ошибок: {}:{}:{}", currentGroupId, currentArtifactId, version);
                        continue;
                    }

                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("message", "Не удалось разрешить зависимость: " + currentGroupId + ":" + currentArtifactId + ":" + version);
                    errorMap.put("file", "");
                    errorMap.put("line", 0);
                    errorMap.put("column", 0);

                    String key = currentGroupId + ":" + currentArtifactId + ":" + version;
                    logger.debug("Обнаружена ошибка Maven зависимости: {}", key);

                    if (!uniqueErrors.contains(key)) {
                        errorList.add(errorMap);
                        uniqueErrors.add(key);
                        logger.debug("Добавлена новая Maven ошибка: {}", key);
                    } else {
                        logger.debug("Пропущена дублирующая Maven ошибка: {}", key);
                    }
                } else {
                    collectArtifacts = false;
                }

                continue;
            }

            Pattern errorPatternOld = Pattern.compile("^\\[ERROR\\]\\s+(.*\\.java):(\\d+):\\s+error:\\s+(.*)$");
            Pattern errorPatternNew = Pattern.compile("^\\[ERROR\\]\\s+(.*\\.java):\\[(\\d+),(\\d+)\\]\\s+(.*)$");
            Pattern mavenErrorPattern = Pattern.compile("^\\[ERROR\\]\\s+(.*)$");

            Matcher matcherOld = errorPatternOld.matcher(line);
            Matcher matcherNew = errorPatternNew.matcher(line);
            Matcher mavenMatcher = mavenErrorPattern.matcher(line);

            if (matcherOld.matches()) {
                String filePath = matcherOld.group(1).trim();
                int lineNumber = Integer.parseInt(matcherOld.group(2).trim());
                String message = matcherOld.group(3).trim();

                Path absolutePath = Paths.get(filePath);
                if (!absolutePath.isAbsolute()) {
                    absolutePath = projectDir.resolve(absolutePath).normalize();
                }

                String relativePath = projectDir.relativize(absolutePath).toString().replace(File.separatorChar, '/');

                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("message", message);
                errorMap.put("file", relativePath);
                errorMap.put("line", lineNumber);
                errorMap.put("column", 0);

                String key = relativePath + ":" + lineNumber + ":" + errorMap.get("column") + ":" + message;
                logger.debug("Обнаружена ошибка (старый формат): {}", key);

                if (!uniqueErrors.contains(key)) {
                    errorList.add(errorMap);
                    uniqueErrors.add(key);
                    logger.debug("Добавлена новая ошибка: {}", key);
                } else {
                    logger.debug("Пропущена дублирующая ошибка: {}", key);
                }
            } else if (matcherNew.matches()) {
                String filePath = matcherNew.group(1).trim();
                int lineNumber = Integer.parseInt(matcherNew.group(2).trim());
                int columnNumber = Integer.parseInt(matcherNew.group(3).trim());
                String message = matcherNew.group(4).trim();

                Path absolutePath = Paths.get(filePath);
                if (!absolutePath.isAbsolute()) {
                    absolutePath = projectDir.resolve(absolutePath).normalize();
                }

                String relativePath = projectDir.relativize(absolutePath).toString().replace(File.separatorChar, '/');

                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("message", message);
                errorMap.put("file", relativePath);
                errorMap.put("line", lineNumber);
                errorMap.put("column", columnNumber);

                String key = relativePath + ":" + lineNumber + ":" + columnNumber + ":" + message;
                logger.debug("Обнаружена ошибка (новый формат): {}", key);

                if (!uniqueErrors.contains(key)) {
                    errorList.add(errorMap);
                    uniqueErrors.add(key);
                    logger.debug("Добавлена новая ошибка: {}", key);
                } else {
                    logger.debug("Пропущена дублирующая ошибка: {}", key);
                }
            } else if (mavenMatcher.matches()) {
                String message = mavenMatcher.group(1).trim();

                if (message.startsWith("To see the full stack trace") ||
                        message.startsWith("Re-run Maven using the") ||
                        message.startsWith("For more information about the errors") ||
                        message.startsWith("[Help") ||
                        message.startsWith("COMPILATION ERROR :") ||
                        message.startsWith("BUILD FAILURE") ||
                        message.startsWith("Failed to execute goal") ||
                        message.startsWith("-> [Help") ||
                        message.startsWith("Please remove or make sure it appears in the correct subdirectory of the sourcepath.") ||
                        message.startsWith("bad source file:") ||
                        message.startsWith("file does not contain class")) {
                    continue;
                }

                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("message", message);
                errorMap.put("file", "");
                errorMap.put("line", 0);
                errorMap.put("column", 0);

                String key = message;
                logger.debug("Обнаружена общая ошибка Maven: {}", key);

                if (!uniqueErrors.contains(key)) {
                    errorList.add(errorMap);
                    uniqueErrors.add(key);
                    logger.debug("Добавлена новая общая ошибка Maven: {}", key);
                } else {
                    logger.debug("Пропущена дублирующая общая ошибка Maven: {}", key);
                }
            }


            if (line.contains("Failed to execute goal")) {
                logger.debug("Достигнут конец блока ошибок компиляции. Остановка парсинга.");
                break;
            }
        }

        logger.debug("Парсинг ошибок завершён. Всего ошибок: {}", errorList.size());
        logger.debug("Список ошибок: {}", errorList);

        return errorList;
    }

    private Map<String, String> extractMavenCoordinates(Path pomPath) {
        Map<String, String> coordinates = new HashMap<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomPath.toFile());

            NodeList groupIdNodes = doc.getElementsByTagName("groupId");
            NodeList artifactIdNodes = doc.getElementsByTagName("artifactId");

            String groupId = null;
            String artifactId = null;

            if (groupIdNodes.getLength() > 0) {
                groupId = groupIdNodes.item(0).getTextContent().trim();
            }

            if (artifactIdNodes.getLength() > 0) {
                artifactId = artifactIdNodes.item(0).getTextContent().trim();
            }

            if (groupId != null && artifactId != null) {
                coordinates.put("groupId", groupId);
                coordinates.put("artifactId", artifactId);
            } else {
                logger.warn("Не удалось извлечь groupId и/или artifactId из '{}'", pomPath.toString());
            }

        } catch (Exception e) {
            logger.error("Ошибка при парсинге pom.xml '{}': {}", pomPath.toString(), e.getMessage());
        }
        return coordinates;
    }

    private ResponseEntity<?> buildSuccessResponse(String stdout, String stderr) {
        Map<String, Object> response = new HashMap<>();
        response.put("stdout", stdout != null ? stdout : "");
        response.put("stderr", stderr != null ? stderr : "");
        response.put("returncode", 0);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> buildErrorResponse(String stdout, List<Map<String, Object>> errors, int returnCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("stdout", stdout != null ? stdout : "");
        response.put("stderr", errors);
        response.put("returncode", returnCode);
        return ResponseEntity.ok(response);
    }

    @Data
    @AllArgsConstructor
    private static class CommandResult {
        private final String stdout;
        private final String stderr;
        private final int returnCode;
    }
}
