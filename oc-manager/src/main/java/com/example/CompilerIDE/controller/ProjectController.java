package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.CompilationService;
import com.example.CompilerIDE.services.MinioService; // Добавлено
import com.example.CompilerIDE.services.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*; // Изменено
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController // Изменено с @Controller на @RestController
@RequestMapping("/projects") // Добавлено
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;
    private final MinioService minioService; // Добавлено

    private final S3Client s3Client;
    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.bucket-name}")
    private String defaultBucketName;
    public ProjectController(ProjectService projectService,
                             ClientService clientService,
                             CompilationService compilationService,
                             MinioService minioService, S3Client s3Client) { // Добавлено
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
        this.minioService = minioService; // Добавлено
        this.s3Client = s3Client;
    }

    // Отображение формы редактирования проекта
    @GetMapping("/edit/{id}")
    public String editProjectForm(@PathVariable("id") int projectId, Model model, Authentication authentication) {
        // Получаем текущего пользователя
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());

        if (clientOpt.isEmpty()) {
            return "redirect:/login";
        }

        Client client = clientOpt.get();

        // Находим проект по ID
        Optional<Project> projectOpt = projectService.findById(projectId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            // Проверяем, принадлежит ли проект текущему пользователю
            if (project.getClient().getId().equals(client.getId())) {
                model.addAttribute("project", project);
                return "edit_project_form"; // Название шаблона для формы редактирования
            } else {
                // Если проект не принадлежит пользователю, перенаправляем на профиль
                return "redirect:/userProfile";
            }
        } else {
            return "redirect:/userProfile";
        }
    }

    // Обработка данных после отправки формы редактирования
    @PostMapping("/edit/{id}")
    public String updateProject(@PathVariable("id") int projectId,
                                @Valid @ModelAttribute("project") Project projectForm,
                                BindingResult bindingResult,
                                Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "edit_project_form"; // Возвращаем обратно на форму, если есть ошибки
        }

        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return "redirect:/login";
        }

        Client client = clientOpt.get();

        Optional<Project> projectOpt = projectService.findById(projectId);

        if (projectOpt.isPresent()) {
            Project existingProject = projectOpt.get();
            if (existingProject.getClient().getId().equals(client.getId())) {
                existingProject.setName(projectForm.getName());
                existingProject.setLanguage(projectForm.getLanguage());
                existingProject.setReadMe(projectForm.getReadMe());
                existingProject.setRefGit(projectForm.getRefGit());
                existingProject.setProjectType(projectForm.getProjectType());
                projectService.save(existingProject);
            }
        }
        return "redirect:/userProfile";
    }
    @GetMapping("/{projectId}/files/{fileName}")
    public ResponseEntity<String> getFileContent(@PathVariable int projectId, @PathVariable String fileName, Authentication authentication) {
        Project project = projectService.findById(projectId).orElse(null);
        if (project == null || !project.getClient().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ запрещён");
        }
        String objectKey = "projects/" + projectId + "/" + fileName;
        String content = minioService.getFileContent(objectKey);
        if (content == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Файл не найден");
        }
        return ResponseEntity.ok(content);
    }

    @PostMapping("/{projectId}/save")
    public ResponseEntity<?> saveProjectFiles(@PathVariable Long projectId,
                                              @RequestParam("files") List<MultipartFile> files,
                                              @RequestParam(value = "basePath", required = false, defaultValue = "") String basePath,
                                              Authentication authentication) {
        // Проверяем, авторизован ли пользователь
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        // Находим проект
        Optional<Project> projectOpt = projectService.findById(projectId.intValue());
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();

        // Проверяем, принадлежит ли проект текущему пользователю
        if (!project.getClient().getId().equals(client.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        try {
            // Создаём бакет, если необходимо
            minioService.createBucket(bucketName);

            // Сохраняем файлы и структуру проекта
            projectService.saveProjectFiles(project, files, basePath, minioService, bucketName);

            return ResponseEntity.ok("Проект успешно сохранён");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении проекта");
        }
    }

    // Компиляция проекта
    @PostMapping("/{projectId}/compile")
    public ResponseEntity<?> compileProject(@PathVariable int projectId,
                                            Authentication authentication) {
        // Проверяем, авторизован ли пользователь
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        // Находим проект
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();

        // Проверяем, принадлежит ли проект текущему пользователю
        if (!project.getClient().getId().equals(client.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        try {
            // Компиляция проекта
            String compilationResult = compilationService.compileProject(project);

            return ResponseEntity.ok(compilationResult);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при компиляции проекта");
        }
    }


    // DTO для переименования файла
    public static class RenameRequest {
        private String oldPath;
        private String newPath;

        // Геттеры и сеттеры
        public String getOldPath() {
            return oldPath;
        }

        public void setOldPath(String oldPath) {
            this.oldPath = oldPath;
        }

        public String getNewPath() {
            return newPath;
        }

        public void setNewPath(String newPath) {
            this.newPath = newPath;
        }
    }

    // DTO для удаления файла
    public static class DeleteRequest {
        private String filePath;

        // Геттеры и сеттеры
        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    @PostMapping("/{projectId}/rename")
    public ResponseEntity<?> renameFile(@PathVariable Long projectId,
                                        @RequestBody RenameRequest renameRequest,
                                        Authentication authentication) {
        // Проверка авторизации
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        // Поиск проекта
        Optional<Project> projectOpt = projectService.findById(projectId.intValue());
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();

        // Проверка принадлежности проекта пользователю
        if (!project.getClient().getId().equals(client.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        String oldObjectKey = "projects/" + projectId + "/" + renameRequest.getOldPath();
        String newObjectKey = "projects/" + projectId + "/" + renameRequest.getNewPath();

        try {
            // Переименование файла или папки: копирование с новым именем и удаление старого
            s3Client.copyObject(builder -> builder
                    .sourceBucket(defaultBucketName)
                    .sourceKey(oldObjectKey)
                    .destinationBucket(defaultBucketName)
                    .destinationKey(newObjectKey)
                    .build());

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(defaultBucketName)
                    .key(oldObjectKey)
                    .build());

            // Обновление структуры проекта в базе данных
            projectService.renameFile(project, renameRequest.getOldPath(), renameRequest.getNewPath());

            return ResponseEntity.ok("Файл успешно переименован");
        } catch (S3Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при переименовании файла");
        }
    }

    // Эндпоинт для удаления файла
    @PostMapping("/{projectId}/delete")
    public ResponseEntity<?> deleteFile(@PathVariable Long projectId,
                                        @RequestBody DeleteRequest deleteRequest,
                                        Authentication authentication) {
        // Проверка авторизации
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        // Поиск проекта
        Optional<Project> projectOpt = projectService.findById(projectId.intValue());
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();

        // Проверка принадлежности проекта пользователю
        if (!project.getClient().getId().equals(client.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        String objectKey = "projects/" + projectId + "/" + deleteRequest.getFilePath();

        try {
            // Удаление файла или папки и всех вложенных файлов
            deleteRecursively(objectKey);

            // Удаление из структуры проекта в базе данных
            projectService.deleteFile(project, deleteRequest.getFilePath());

            return ResponseEntity.ok("Файл успешно удалён");
        } catch (S3Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при удалении файла");
        }
    }

    // Рекурсивное удаление файлов и папок
    private void deleteRecursively(String objectKey) {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(defaultBucketName)
                .prefix(objectKey)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        for (S3Object s3Object : listResponse.contents()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(defaultBucketName)
                    .key(s3Object.key())
                    .build());
        }

        // Также удаляем сам объект, если он не содержит других объектов
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(defaultBucketName)
                .key(objectKey)
                .build());
    }


}
