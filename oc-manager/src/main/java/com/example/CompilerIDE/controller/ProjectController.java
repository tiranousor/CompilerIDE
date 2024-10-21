package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.CompileRequest;
import com.example.CompilerIDE.Dto.JsTreeNode;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.CompilationService;
import com.example.CompilerIDE.services.MinioService;
import com.example.CompilerIDE.services.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLConnection;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;
    private final MinioService minioService;
    private final ProjectStructRepository projectStructRepository;
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    @Value("${minio.bucket-name}")
    private String bucketName; // Убедитесь, что бакет создан

    public ProjectController(ProjectService projectService,
                             ClientService clientService,
                             CompilationService compilationService,
                             MinioService minioService,
                             ProjectStructRepository projectStructRepository) {
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
        this.minioService = minioService;
        this.projectStructRepository = projectStructRepository;
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

    // Получение файлов и папок в формате jsTree
    @GetMapping("/{projectId}/files")
    public ResponseEntity<?> getFiles(
            @PathVariable("projectId") int projectId,
            @RequestParam(value = "path", required = false) String path,
            Authentication authentication) {

        // Проверка авторизации
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

        // Проверка безопасности пути
        if (path.contains("..")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid file path");
        }

        List<ProjectStruct> structs;

        if (path == null || path.isEmpty()) {
            // Получаем корневые файлы и папки
            structs = projectStructRepository.findByProjectAndPathNotContaining(project, "/");
        } else {
            // Получаем дочерние файлы и папки
            String pathPrefix = path.endsWith("/") ? path : path + "/";
            structs = projectStructRepository.findByProjectAndPathStartingWith(project, pathPrefix);
        }

        try {
            // Преобразование данных в формат jsTree
            List<JsTreeNode> response = structs.stream().map(struct -> new JsTreeNode(
                    struct.getPath(),
                    struct.getName(),
                    struct.isFolder(),
                    struct.isFolder() ? "default" : "file"
            )).toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error processing files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при обработке данных");
        }
    }

    // Создание файла или папки
    @PostMapping("/{projectId}/files/create")
    public ResponseEntity<?> createFileOrFolder(
            @PathVariable("projectId") int projectId,
            @RequestBody JsonNode requestBody,
            Authentication authentication) {

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

        String parent = requestBody.get("parent").asText();
        String text = requestBody.get("text").asText();
        String type = requestBody.get("type").asText(); // 'default' для папок, 'file' для файлов

        // Формируем полный путь
        String newPath = parent.isEmpty() ? text : parent + "/" + text;

        // Проверяем, существует ли уже файл или папка с таким именем
        Optional<ProjectStruct> existing = projectStructRepository.findByProjectAndPath(project, newPath);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Файл или папка с таким именем уже существуют");
        }

        try {
            ProjectStruct struct;
            if (type.equals("default")) {
                // Создаём папку
                struct = new ProjectStruct();
                struct.setProject(project);
                struct.setPath(newPath);
                struct.setName(text);
                struct.setType("folder");
                projectStructRepository.save(struct);

                // Создаём объект в MinIO (пустая папка)
                String objectKey = "projects/" + projectId + "/" + newPath + "/";
                minioService.createFolder(objectKey);

            } else if (type.equals("file")) {
                // Создаём файл
                struct = new ProjectStruct();
                struct.setProject(project);
                struct.setPath(newPath);
                struct.setName(text);
                struct.setType("file");
                projectStructRepository.save(struct);

                // Создаём пустой файл в MinIO
                String objectKey = "projects/" + projectId + "/" + newPath;
                minioService.createFile(objectKey, "");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid type");
            }

            // Возвращаем созданный узел с id
            JsTreeNode jsTreeNode = new JsTreeNode(
                    newPath,
                    text,
                    type.equals("default"),
                    type
            );

            return ResponseEntity.ok(jsTreeNode);

        } catch (Exception e) {
            logger.error("Error creating file or folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при создании файла/папки");
        }
    }

    // Переименование файла или папки
    @PutMapping("/{projectId}/files/rename")
    public ResponseEntity<?> renameFileOrFolder(
            @PathVariable("projectId") int projectId,
            @RequestBody JsonNode requestBody,
            Authentication authentication) {

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

        String path = requestBody.get("path").asText();
        String newName = requestBody.get("newName").asText();

        // Проверяем, существует ли файл или папка
        Optional<ProjectStruct> structOpt = projectStructRepository.findByProjectAndPath(project, path);
        if (structOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Файл или папка не найдены");
        }

        ProjectStruct struct = structOpt.get();

        // Формируем новый путь
        String parentPath = "";
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            parentPath = path.substring(0, lastSlash);
        }
        String newPath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;

        // Проверяем, существует ли уже файл или папка с новым именем
        Optional<ProjectStruct> existing = projectStructRepository.findByProjectAndPath(project, newPath);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Файл или папка с таким именем уже существуют");
        }

        try {
            // Переименовываем запись в базе данных
            struct.setPath(newPath);
            struct.setName(newName);
            projectStructRepository.save(struct);

            // Переименовываем объект в MinIO
            String oldObjectKey = "projects/" + projectId + "/" + path;
            String newObjectKey = "projects/" + projectId + "/" + newPath;
            if(struct.getType().equals("folder")) {
                // Для папки добавляем '/' в конце
                oldObjectKey += "/";
                newObjectKey += "/";
            }
            minioService.renameObject(oldObjectKey, newObjectKey);

            return ResponseEntity.ok("Переименовано успешно");
        } catch (Exception e) {
            logger.error("Error renaming file or folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при переименовании файла/папки");
        }
    }

    // Удаление файла или папки
    @DeleteMapping("/{projectId}/files/delete")
    public ResponseEntity<?> deleteFileOrFolder(
            @PathVariable("projectId") int projectId,
            @RequestBody JsonNode requestBody,
            Authentication authentication) {

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

        String path = requestBody.get("path").asText();

        // Проверяем, существует ли файл или папка
        Optional<ProjectStruct> structOpt = projectStructRepository.findByProjectAndPath(project, path);
        if (structOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Файл или папка не найдены");
        }

        ProjectStruct struct = structOpt.get();

        try {
            // Удаляем запись из базы данных
            projectStructRepository.delete(struct);

            // Удаляем объект из MinIO
            String objectKey = "projects/" + projectId + "/" + path;
            if(struct.getType().equals("folder")) {
                objectKey += "/";
            }
            minioService.deleteObject(objectKey);

            return ResponseEntity.ok("Удалено успешно");
        } catch (Exception e) {
            logger.error("Error deleting file or folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при удалении файла/папки");
        }
    }

    // Загрузка файла для скачивания
    @PostMapping("/{projectId}/files/download")
    public ResponseEntity<?> downloadFile(
            @PathVariable("projectId") int projectId,
            @RequestBody JsonNode requestBody,
            Authentication authentication) {

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

        String filePath = requestBody.get("path").asText();

        // Проверка безопасности пути
        if (filePath.contains("..")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid file path");
        }

        // Поиск файла в базе данных
        Optional<ProjectStruct> projectStructOpt = projectStructRepository.findByProjectAndPathAndType(project, filePath, "file");
        if (projectStructOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        ProjectStruct projectStruct = projectStructOpt.get();
        String objectKey = "projects/" + projectId + "/" + projectStruct.getPath();

        try {
            // Получение содержимого файла из MinIO
            byte[] fileContent = minioService.getFileContentAsBytes(objectKey);
            if (fileContent == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving file content");
            }

            // Определяем MIME тип файла
            String mimeType = URLConnection.guessContentTypeFromName(filePath);
            if (mimeType == null) {
                mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE; // По умолчанию
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + projectStruct.getName() + "\"")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(fileContent);
        } catch (Exception e) {
            logger.error("Error downloading file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error downloading file");
        }
    }

    // Компиляция проекта
    @PostMapping("/{projectId}/compile")
    public ResponseEntity<?> compileProject(@PathVariable int projectId,
                                            @RequestBody CompileRequest compileRequest,
                                            Authentication authentication) {
        // Проверка авторизации
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
            logger.error("Error compiling project", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при компиляции проекта");
        }
    }

    // Загрузка файлов через jsTree (можно использовать существующий метод saveProject)
    @PostMapping("/{projectId}/save")
    public ResponseEntity<?> saveProjectFiles(@PathVariable int projectId,
                                              @RequestParam("files") List<MultipartFile> files,
                                              @RequestParam(value = "basePath", required = false, defaultValue = "") String basePath,
                                              Authentication authentication) {
        // Проверка авторизации
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
            // Сохраняем файлы и структуру проекта
            projectService.saveProjectFiles(project, files, basePath);
            return ResponseEntity.ok("Проект успешно сохранён");
        } catch (Exception e) {
            logger.error("Error saving project files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении проекта");
        }
    }
}
