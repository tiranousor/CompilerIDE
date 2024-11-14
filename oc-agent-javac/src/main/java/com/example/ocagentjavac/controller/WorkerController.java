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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.*;

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
        try {
            String projectId = payload.get("project_id");
            String language = payload.get("language");

            if (projectId == null || language == null) {
                return ResponseEntity.badRequest().body("project_id и language обязательны");
            }

            if (!"java".equalsIgnoreCase(language)) {
                return ResponseEntity.badRequest().body("Текущая поддержка только языка Java");
            }

            Path projectDir = Paths.get("/tmp", projectId);
            String projectPrefix = "projects/" + projectId + "/";

            try {
                FileSystemUtils.deleteRecursively(projectDir.toFile());
                Files.createDirectories(projectDir);

                downloadProjectFiles(projectPrefix, projectDir);

                Path pomPath = projectDir.resolve("pom.xml");
                boolean isMavenProject = Files.exists(pomPath);

                long compileTimeout = 60;
                long runTimeout = 30;
                String compileOutput;
                int compileExitCode;

                if (isMavenProject) {
                    boolean isMainClassDefined = isMainClassDefinedInPom(pomPath);
                    if (isMainClassDefined) {
                        ProcessBuilder compileBuilder = new ProcessBuilder("mvn", "clean", "compile");
                        compileBuilder.directory(projectDir.toFile());
                        compileBuilder.redirectErrorStream(true);
                        Process compileProcess = compileBuilder.start();
                        compileOutput = readProcessOutput(compileProcess.getInputStream(), compileTimeout, TimeUnit.SECONDS);
                        boolean compileFinished = compileProcess.waitFor(compileTimeout, TimeUnit.SECONDS);
                        compileExitCode = compileProcess.waitFor();

                        if (compileExitCode != 0) {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                    "stdout", compileOutput,
                                    "stderr", "Compilation failed or timed out",
                                    "returncode", compileExitCode
                            ));
                        }

                        ProcessBuilder runBuilder = new ProcessBuilder("mvn", "exec:java");
                        runBuilder.directory(projectDir.toFile());
                        runBuilder.redirectErrorStream(true);
                        Process runProcess = runBuilder.start();
                        String runOutput = readProcessOutput(runProcess.getInputStream(), runTimeout, TimeUnit.SECONDS);
                        boolean runFinished = runProcess.waitFor(runTimeout, TimeUnit.SECONDS);
                        int runExitCode = runProcess.exitValue();

                        if (!runFinished) {
                            runProcess.destroyForcibly();
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                    "stdout", runOutput,
                                    "stderr", "Execution timed out",
                                    "returncode", runExitCode
                            ));
                        }
                        return ResponseEntity.ok(Map.of(
                                "stdout", runOutput,
                                "stderr", "",
                                "returncode", runExitCode
                        ));
                    } else {
                        Optional<String> mainClass = findMainClassInJavaFiles(projectDir);
                        if (mainClass.isEmpty()) {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                    "error", "Не найден класс с методом main в .java файлах"
                            ));
                        }

                        ProcessBuilder compileBuilder = new ProcessBuilder("mvn", "clean", "compile");
                        compileBuilder.directory(projectDir.toFile());
                        compileBuilder.redirectErrorStream(true);
                        Process compileProcess = compileBuilder.start();
                        compileOutput = readProcessOutput(compileProcess.getInputStream(), compileTimeout, TimeUnit.SECONDS);
                        boolean compileFinished = compileProcess.waitFor(compileTimeout, TimeUnit.SECONDS);
                        compileExitCode = compileProcess.waitFor();

                        if (compileExitCode != 0) {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                    "stdout", compileOutput,
                                    "stderr", "Compilation failed or timed out",
                                    "returncode", compileExitCode
                            ));
                        }

                        ProcessBuilder runBuilder = new ProcessBuilder("java", "-cp", "target/classes", mainClass.get());
                        runBuilder.directory(projectDir.toFile());
                        runBuilder.redirectErrorStream(true);
                        Process runProcess = runBuilder.start();
                        String runOutput = readProcessOutput(runProcess.getInputStream(), runTimeout, TimeUnit.SECONDS);
                        boolean runFinished = runProcess.waitFor(runTimeout, TimeUnit.SECONDS);
                        int runExitCode = runProcess.exitValue();

                        if (!runFinished) {
                            runProcess.destroyForcibly();
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                    "stdout", runOutput,
                                    "stderr", "Execution timed out",
                                    "returncode", runExitCode
                            ));
                        }
                        ;

                        return ResponseEntity.ok(Map.of(
                                "stdout", runOutput,
                                "stderr", "",
                                "returncode", runExitCode
                        ));
                    }
                } else {
                    List<Path> javaFiles = new ArrayList<>();
                    Files.walk(projectDir)
                            .filter(path -> path.toString().endsWith(".java"))
                            .forEach(javaFiles::add);

                    if (javaFiles.isEmpty()) {
                        return ResponseEntity.badRequest().body("Нет .java файлов для компиляции");
                    }

                    List<String> javacCommand = new ArrayList<>();
                    javacCommand.add("javac");
                    for (Path javaFile : javaFiles) {
                        javacCommand.add(javaFile.toString());
                    }

                    ProcessBuilder javacBuilder = new ProcessBuilder(javacCommand);
                    javacBuilder.directory(projectDir.toFile());
                    javacBuilder.redirectErrorStream(true);
                    Process javacProcess = javacBuilder.start();
                    compileOutput = readProcessOutput(javacProcess.getInputStream(), compileTimeout, TimeUnit.SECONDS);
                    boolean compileFinished = javacProcess.waitFor(compileTimeout, TimeUnit.SECONDS);
                    compileExitCode = javacProcess.exitValue();

                    if (!compileFinished || compileExitCode != 0) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                "stdout", compileOutput,
                                "stderr", "Compilation failed or timed out",
                                "returncode", compileExitCode
                        ));
                    }

                    Optional<String> mainClass = findMainClassInJavaFiles(projectDir);

                    if (mainClass.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                "error", "Не найден класс с методом main"
                        ));
                    }

                    ProcessBuilder runBuilder = new ProcessBuilder("java", "-cp", projectDir.toString(), mainClass.get());
                    runBuilder.directory(projectDir.toFile());
                    runBuilder.redirectErrorStream(true);
                    Process runProcess = runBuilder.start();
                    String runOutput = readProcessOutput(runProcess.getInputStream(), runTimeout, TimeUnit.SECONDS);
                    boolean runFinished = runProcess.waitFor(runTimeout, TimeUnit.SECONDS);
                    int runExitCode = runProcess.exitValue();
                    if (!runFinished) {
                        logger.info("Ошибка отправки результата компиляции: stdout={}, stderr={}, returncode={}", runOutput, "", runExitCode);
                        runProcess.destroyForcibly();
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                                "stdout", runOutput,
                                "stderr", "Execution timed out",
                                "returncode", runExitCode
                        ));
                    }
                    logger.info("Отправка результата компиляции: stdout={}, stderr={}, returncode={}", runOutput, "", runExitCode);
                    return ResponseEntity.ok(Map.of(
                            "stdout", runOutput,
                            "stderr", "",
                            "returncode", runExitCode
                    ));
                }

            } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
                logger.error("MinioException при компиляции проекта '{}': {}", projectId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
            } catch (IOException e) {
                logger.error("IOException при компиляции проекта '{}': {}", projectId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
            } catch (InterruptedException e) {
                logger.error("InterruptedException при компиляции проекта '{}': {}", projectId, e.getMessage());
                Thread.currentThread().interrupt();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Процесс был прерван"));
            } catch (Exception e) {
                logger.error("Ошибка при компиляции проекта '{}': {}", projectId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Ошибка компиляции проекта"));
            } finally {
                FileSystemUtils.deleteRecursively(projectDir.toFile());
                logger.info("Временные файлы проекта '{}' успешно удалены.", projectId);
            }
        }catch (Exception e) {
            logger.error("Unexpected exception in compileProject: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private Optional<String> findMainClassInJavaFiles(Path projectDir) throws IOException {
        Pattern mainPattern = Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*\\[\\]\\s*args\\s*\\)");

        List<Path> javaFiles = new ArrayList<>();
        Files.walk(projectDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFiles::add);

        for (Path javaFile : javaFiles) {
            boolean hasMain = false;
            String packageName = "";

            try (BufferedReader reader = Files.newBufferedReader(javaFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("package ")) {
                        int start = "package ".length();
                        int end = line.indexOf(';', start);
                        if (end > start) {
                            packageName = line.substring(start, end).trim();
                        }
                    }

                    Matcher matcher = mainPattern.matcher(line);
                    if (matcher.find()) {
                        hasMain = true;
                        break;
                    }
                }
            } catch (IOException e) {
                logger.error("Ошибка при чтении файла '{}': {}", javaFile, e.getMessage());
                continue;
            }

            if (hasMain) {
                String className = javaFile.getFileName().toString().replace(".java", "");
                String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
                logger.info("Найден класс с методом main: {}", fullyQualifiedName);
                return Optional.of(fullyQualifiedName);
            }
        }

        return Optional.empty();
    }

    private boolean isMainClassDefinedInPom(Path pomPath) {
        try {
            File pomFile = pomPath.toFile();
            if (!pomFile.exists()) {
                return false;
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);
            doc.getDocumentElement().normalize();

            NodeList pluginList = doc.getElementsByTagName("plugin");
            for (int i = 0; i < pluginList.getLength(); i++) {
                Node pluginNode = pluginList.item(i);
                if (pluginNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element pluginElement = (Element) pluginNode;
                    String artifactId = pluginElement.getElementsByTagName("artifactId").item(0).getTextContent();
                    if ("exec-maven-plugin".equals(artifactId)) {
                        NodeList configList = pluginElement.getElementsByTagName("configuration");
                        if (configList.getLength() > 0) {
                            Element configElement = (Element) configList.item(0);
                            NodeList mainClassList = configElement.getElementsByTagName("mainClass");
                            if (mainClassList.getLength() > 0) {
                                String mainClass = mainClassList.item(0).getTextContent().trim();
                                if (!mainClass.isEmpty()) {
                                    logger.info("mainClass определён в pom.xml: {}", mainClass);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            logger.info("mainClass не определён в pom.xml.");
            return false;
        } catch (Exception e) {
            logger.error("Ошибка при чтении pom.xml: {}", e.getMessage());
            return false;
        }
    }

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

    private String readProcessOutput(InputStream inputStream, long timeout, TimeUnit unit) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder out = new StringBuilder();
        long endTime = System.nanoTime() + unit.toNanos(timeout);
        String line;
        while ((line = reader.readLine()) != null && System.nanoTime() < endTime) {
            out.append(line).append("\n");
        }
        return out.toString();
    }
}
