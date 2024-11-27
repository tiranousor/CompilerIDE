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

            Path projectDir = Paths.get("/tmp", projectId);
            String projectPrefix = "projects/" + projectId + "/";

            try {
                prepareProjectDirectory(projectDir);
                downloadProjectFiles(projectPrefix, projectDir);

                if (!isValidMainClass(mainClassName, projectDir)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Класс " + mainClassName + " не найден или некорректен"));
                }

                boolean isMavenProject = Files.exists(projectDir.resolve("pom.xml"));

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
            String classPath = mainClassName.replace('.', '/') + ".java";
            Path classFilePath = projectDir.resolve(classPath);
            return Files.exists(classFilePath);
        }

        private ResponseEntity<?> handleMavenProject(Path projectDir, String mainClassName) throws IOException, InterruptedException {
            // Компиляция Maven-проекта
            CommandResult compileResult = executeCommand(Arrays.asList("mvn", "-e", "clean", "compile"), projectDir, 60);
            if (compileResult.getReturnCode() != 0) {
                List<Map<String, Object>> errors = parseJavacErrors(compileResult.getStderr(), projectDir);
                return buildErrorResponse(compileResult.getStdout(), errors, compileResult.getReturnCode());
            }
            // Сбор classpath
            CommandResult classpathResult = executeCommand(Arrays.asList("mvn", "dependency:build-classpath", "-Dmdep.outputFile=classpath.txt"), projectDir, 30);
            if (classpathResult.getReturnCode() != 0) {
                return buildErrorResponse(classpathResult.getStdout(), classpathResult.getStderr(), classpathResult.getReturnCode());
            }

            Path classpathFile = projectDir.resolve("classpath.txt");
            if (!Files.exists(classpathFile)) {
                return buildErrorResponse("", "Не удалось получить classpath", 1);
            }

            String classpath = Files.readString(classpathFile).trim();
            if (classpath.isEmpty()) {
                return buildErrorResponse("", "Classpath пустой", 1);
            }

            // Запуск главного класса
            List<String> javaRunCommand = Arrays.asList("java", "-cp", classpath + File.pathSeparator + "target/classes", mainClassName);
            CommandResult runResult = executeCommand(javaRunCommand, projectDir, 30);
            if (runResult.getReturnCode() != 0) {
                return buildErrorResponse(runResult.getStdout(), runResult.getStderr(), runResult.getReturnCode());
            }

            return buildSuccessResponse(runResult.getStdout(), runResult.getStderr());
        }

        private ResponseEntity<?> handleNonMavenProject(Path projectDir, String mainClassName) throws IOException, InterruptedException {
            List<Path> javaFiles = collectJavaFiles(projectDir);

            List<String> javacCommand = new ArrayList<>();
            javacCommand.add("javac");
            for (Path javaFile : javaFiles) {
                javacCommand.add(javaFile.toString());
            }

            CommandResult compileResult = executeCommand(javacCommand, projectDir, 60);
            if (compileResult.getReturnCode() != 0) {
                List<Map<String, Object>> errors = parseJavacErrors(compileResult.getStderr(), projectDir);
                return buildErrorResponse(compileResult.getStdout(), errors, compileResult.getReturnCode());
            }


            List<String> javaRunCommand = Arrays.asList("java", "-cp", ".", mainClassName);
            CommandResult runResult = executeCommand(javaRunCommand, projectDir, 30);
            if (runResult.getReturnCode() != 0) {
                return buildErrorResponse(runResult.getStdout(), runResult.getStderr(), runResult.getReturnCode());
            }

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

            return new CommandResult(stdout.toString(), stderr.toString(), exitCode);
        }

        private List<Map<String, Object>> parseJavacErrors(String stderrOutput, Path projectDir) {
            List<Map<String, Object>> errorList = new ArrayList<>();

            if (stderrOutput == null || stderrOutput.isEmpty()) {
                return errorList;
            }

            String[] lines = stderrOutput.split("\n");
            Pattern errorPattern = Pattern.compile("^(.*\\.java):(\\d+): error: (.*)$");

            String projectDirPath = projectDir.toString();

            for (int i = 0; i < lines.length; i++) {
                Matcher matcher = errorPattern.matcher(lines[i]);
                if (matcher.matches()) {
                    String filePath = matcher.group(1).trim();
                    int lineNumber = Integer.parseInt(matcher.group(2).trim());
                    String message = matcher.group(3).trim();

                    // Удаляем префикс временной директории из пути файла
                    if (filePath.startsWith(projectDirPath)) {
                        filePath = filePath.substring(projectDirPath.length());
                    }

                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("message", message);
                    errorMap.put("file", filePath);
                    errorMap.put("line", lineNumber);
                    errorMap.put("column", 0); // `javac` обычно не указывает столбец

                    // Попробуем извлечь информацию о столбце
                    if (i + 2 < lines.length) {
                        String codeLine = lines[i + 1]; // строка кода с ошибкой
                        String pointerLine = lines[i + 2]; // строка с указателем '^'
                        int columnNumber = pointerLine.indexOf('^') + 1;
                        if (columnNumber > 0) {
                            errorMap.put("column", columnNumber);
                        }
                    }

                    errorList.add(errorMap);
                }
            }

            return errorList;
        }


        private ResponseEntity<?> buildSuccessResponse(String stdout, String stderr) {
            Map<String, Object> response = new HashMap<>();
            response.put("stdout", stdout != null ? stdout : "");
            response.put("stderr", stderr != null ? stderr : "");
            response.put("returncode", 0);
            return ResponseEntity.ok(response);
        }

        private ResponseEntity<?> buildErrorResponse(String stdout, Object stderr, int returnCode) {
            Map<String, Object> response = new HashMap<>();
            response.put("stdout", stdout != null ? stdout : "");
            response.put("stderr", stderr != null ? stderr : "");
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
