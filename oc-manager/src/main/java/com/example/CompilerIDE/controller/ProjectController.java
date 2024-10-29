package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectAccessLog;
import com.example.CompilerIDE.providers.ProjectStruct;
import com.example.CompilerIDE.repositories.ProjectAccessLogRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.CompilationService;
import com.example.CompilerIDE.services.MinioService; // Добавлено
import com.example.CompilerIDE.services.ProjectService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*; // Изменено
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.URLConnection;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Controller // Изменено с @Controller на @RestController
@RequestMapping("/projects") // Добавлено
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;
    private final MinioService minioService; // Добавлено
    private final ProjectStructRepository projectStructRepository;
    private final ProjectAccessLogRepository projectAccessLogRepository;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    @Value("${minio.bucket-name}")
    private String bucketName; // Убедитесь, что бакет создан

    public ProjectController(ProjectService projectService,
                             ClientService clientService,
                             CompilationService compilationService,
                             MinioService minioService, ProjectStructRepository projectStructRepository, ProjectAccessLogRepository projectAccessLogRepository) { // Добавлено
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
        this.minioService = minioService; // Добавлено
        this.projectStructRepository = projectStructRepository;
        this.projectAccessLogRepository = projectAccessLogRepository;
    }

//    // Отображение формы редактирования проекта
//    @GetMapping("/edit/{id}")
//    public String editProjectForm(@PathVariable("id") int projectId, Model model, Authentication authentication) {
//        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
//
//        if (clientOpt.isEmpty()) {
//            return "redirect:/login";
//        }
//
//        Client client = clientOpt.get();
//
//        // Находим проект по ID
//        Optional<Project> projectOpt = projectService.findById(projectId);
//
//        if (projectOpt.isPresent()) {
//            Project project = projectOpt.get();
//            // Проверяем, принадлежит ли проект текущему пользователю
//            if (project.getClient().getId().equals(client.getId())) {
//                model.addAttribute("project", project);
//                return "edit_project_form"; // Название шаблона для формы редактирования
//            } else {
//                // Если проект не принадлежит пользователю, перенаправляем на профиль
//                return "redirect:/userProfile";
//            }
//        } else {
//            return "redirect:/userProfile";
//        }
//    }
@GetMapping("/edit/{id}")
public String editProjectForm(@PathVariable("id") int projectId, Model model, Authentication authentication) {
    Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());

    if (clientOpt.isEmpty()) {
        return "redirect:/login";
    }

    Client client = clientOpt.get();

    Optional<Project> projectOpt = projectService.findById(projectId);
    if (projectOpt.isPresent()) {
        Project project = projectOpt.get();

        // Проверяем, принадлежит ли проект текущему пользователю
        if (project.getClient().getId().equals(client.getId())) {

            // Записываем информацию о том, что проект был открыт
            ProjectAccessLog accessLog = new ProjectAccessLog();
            accessLog.setClient(client);
            accessLog.setProject(project);
            accessLog.setAccessTime(new Timestamp(System.currentTimeMillis()));  // Текущее время
            accessLog.setActionType("open");
            projectAccessLogRepository.save(accessLog);

            model.addAttribute("project", project);
            return "edit_project_form"; // Страница редактирования проекта
        } else {
            return "redirect:/userProfile";
        }
    } else {
        return "redirect:/userProfile";
    }
}

//
//
//    @PostMapping("/edit/{id}")
//    public String updateProject(@PathVariable("id") int projectId,
//                                @Valid @ModelAttribute("project") Project projectForm,
//                                BindingResult bindingResult,
//                                Authentication authentication) {
//        if (bindingResult.hasErrors()) {
//            return "edit_project_form"; // Возвращаем обратно на форму, если есть ошибки
//        }
//
//        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
//        if (clientOpt.isEmpty()) {
//            return "redirect:/login";
//        }
//
//        Client client = clientOpt.get();
//
//        Optional<Project> projectOpt = projectService.findById(projectId);
//
//        if (projectOpt.isPresent()) {
//            Project existingProject = projectOpt.get();
//            if (existingProject.getClient().getId().equals(client.getId())) {
//                existingProject.setName(projectForm.getName());
//                existingProject.setLanguage(projectForm.getLanguage());
//                existingProject.setReadMe(projectForm.getReadMe());
//                existingProject.setRefGit(projectForm.getRefGit());
//                existingProject.setProjectType(projectForm.getProjectType());
//                projectService.save(existingProject);
//            }
//        }
//        return "redirect:/userProfile";
//    }

    @PostMapping("/edit/{id}")
    public String updateProject(@PathVariable("id") int projectId,
                                @Valid @ModelAttribute("project") Project projectForm,
                                BindingResult bindingResult,
                                Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "edit_project_form";
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

                // Записываем информацию об изменении проекта
                ProjectAccessLog accessLog = new ProjectAccessLog();
                accessLog.setClient(client);
                accessLog.setProject(existingProject);
                accessLog.setAccessTime(new Timestamp(System.currentTimeMillis()));  // Текущее время
                accessLog.setActionType("edit");  // Действие "edit"
                projectAccessLogRepository.save(accessLog);
            }
        }
        return "redirect:/userProfile";
    }


    @GetMapping("/{projectId}/access-logs")
    @ResponseBody
    public ResponseEntity<List<ProjectAccessLog>> getProjectAccessLogs(@PathVariable("projectId") int projectId) {
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<ProjectAccessLog> accessLogs = projectAccessLogRepository.findByProject(projectOpt.get());
        return ResponseEntity.ok(accessLogs);
    }


    @GetMapping("/{projectId}/files")
    @ResponseBody
    public ResponseEntity<?> getFile(
            @PathVariable("projectId") int projectId,
            @RequestParam("path") String filePath) {

        try {
            logger.info("Received request for file: {}", filePath);

            // Проверка на безопасность пути
            if (filePath.contains("..")) {
                logger.warn("Invalid file path detected: {}", filePath);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid file path");
            }

            // Поиск файла в базе данных
            Optional<ProjectStruct> projectStructOpt = projectStructRepository.findByProjectIdAndPathAndType(projectId, filePath, "file");
            if (projectStructOpt.isEmpty()) {
                logger.warn("File not found: {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
            }

            ProjectStruct projectStruct = projectStructOpt.get();
            String objectKey = "projects/" + projectId + "/" + projectStruct.getPath();

            logger.info("Fetching file from MinIO with key: {}", objectKey);

            // Получение содержимого файла из MinIO
            byte[] fileContent = minioService.getFileContentAsBytes(objectKey);
            if (fileContent == null) {
                logger.error("Error retrieving file content for key: {}", objectKey);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving file content");
            }

            // Определяем MIME тип файла
            String mimeType = URLConnection.guessContentTypeFromName(filePath);
            if (mimeType == null) {
                mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE; // По умолчанию
            }

            logger.info("Successfully retrieved file content for: {}", filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(fileContent);
        } catch (Exception e) {
            logger.error("Exception occurred while retrieving file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }


    @PostMapping("/{projectId}/save")
    @ResponseBody
    public ResponseEntity<?> saveProjectFiles(@PathVariable Long projectId,
                                              @RequestParam("files") List<MultipartFile> files,
                                              @RequestParam(value = "basePath", required = false, defaultValue = "") String basePath,
                                              Authentication authentication) {
        // Проверяем, авторизован ли пользователь
        System.out.println(files);
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
            // Сохраняем файлы и структуру проекта
            projectService.saveProjectFiles(project, files, basePath);
            return ResponseEntity.ok("Проект успешно сохранён");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении проекта");
        }
    }

    // Компиляция проекта
    @PostMapping("/{projectId}/compile")
    @ResponseBody
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
}