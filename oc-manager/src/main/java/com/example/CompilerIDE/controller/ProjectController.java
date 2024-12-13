package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.dto.FileNodeDto;
import com.example.CompilerIDE.dto.JsTreeNodeDto;
import com.example.CompilerIDE.dto.SaveProjectRequest;
import com.example.CompilerIDE.providers.*;
import com.example.CompilerIDE.repositories.ProjectAccessLogRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.services.*;
import com.example.CompilerIDE.util.FileUploadUtil;
import com.example.CompilerIDE.util.ProjectValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/projects")
public class ProjectController  {
    private final ProjectInvitationService projectInvitationService;
    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;
    private final MinioService minioService;
    private final ObjectMapper objectMapper;
    private final ProjectStructRepository projectStructRepository;
    private final ProjectTeamService projectTeamService;
    private final ProjectValidator projectValidator;

    private final ProjectAccessLogRepository projectAccessLogRepository;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    @Value("${minio.bucket-name}")
    private String bucketName;

    public ProjectController(ProjectInvitationService projectInvitationService, ProjectService projectService,
                             ClientService clientService,
                             CompilationService compilationService,
                             MinioService minioService, ObjectMapper objectMapper, ProjectStructRepository projectStructRepository, ProjectTeamService projectTeamService, ProjectValidator projectValidator, ProjectAccessLogRepository projectAccessLogRepository) {
        this.projectInvitationService = projectInvitationService;
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
        this.minioService = minioService;
        this.objectMapper = objectMapper;
        this.projectStructRepository = projectStructRepository;
        this.projectTeamService = projectTeamService;
        this.projectValidator = projectValidator;
        this.projectAccessLogRepository = projectAccessLogRepository;
    }
    @GetMapping("/info/{id}")
    public String projectInfo(@PathVariable("id") int projectId,
                              Model model,
                              Authentication authentication) {
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return "redirect:/userProfile";
        }

        Project project = projectOpt.get();
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return "redirect:/login";
        }

        Client currentUser = clientOpt.get();

        // Используем ProjectTeamService через ProjectService
        ProjectTeamService projectTeamService = projectService.getProjectTeamService();
        Optional<ProjectTeam> projectTeamOpt = projectTeamService.findByProjectAndClient(project, currentUser);

        boolean isOwner = projectTeamOpt.isPresent() && projectTeamOpt.get().getRole() == ProjectTeam.Role.CREATOR;
        boolean isCollaborator = projectTeamOpt.isPresent() && projectTeamOpt.get().getRole() == ProjectTeam.Role.COLLABORATOR;

        model.addAttribute("project", project);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("isCollaborator", isCollaborator);

        return "projectInfo";
    }
    @GetMapping("/userProfile/new")
    public String newProjectForm(Authentication authentication, Model model) {
        model.addAttribute("project", new Project());
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("client", client);

        return "new_project_form";
    }

    @PostMapping("/userProfile/new")
    public String createProject(@Valid @ModelAttribute("project") Project project, BindingResult bindingResult, Authentication authentication, Model model) {
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        if (client == null) {
            return "redirect:/login";
        }
        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("client", client);
            model.addAttribute("project", new Project());
            return "new_project_form";
        }
        project.setClient(client);
        projectService.save(project);
        if (project.getRefGit() != null && !project.getRefGit().isEmpty()) {
            try {
                projectService.importFromGit(project);
            } catch (Exception e) {
                logger.error("Ошибка при импорте проекта из Git: {}", e.getMessage());
                projectService.delete(project);
                bindingResult.rejectValue("refGit", "error.refGit", "Не удалось импортировать проект из Git: " + e.getMessage());
                // Добавляем client в модель
                model.addAttribute("client", client);
                return "new_project_form";
            }
        }
        projectTeamService.addCreator(project, clientService.findByUsername(authentication.getName()).get());

        return "redirect:/userProfile";
    }


    @PostMapping("/delete/{id}")
    public String deleteProject(@PathVariable("id") int projectId, Authentication authentication) {
        Client client = clientService.findByUsername(authentication.getName()).get();
        Optional<Project> projectToDelete = projectService.findById(projectId);

        if (projectToDelete.isPresent() && projectToDelete.get().getClient().getId() == client.getId()) {
            projectService.delete(projectToDelete.get());
        }

        return "redirect:/userProfile";
    }

    @GetMapping("/edit/{id}")
    public String editProjectForm(@PathVariable("id") int projectId, Model model, Authentication authentication) {
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());

        if (clientOpt.isEmpty()) {
            return "redirect:/login";
        }

        Client client = clientOpt.get();
        model.addAttribute("client", client);
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            if (project.getClient().getId().equals(client.getId())) {
                ProjectAccessLog accessLog = new ProjectAccessLog();
                accessLog.setClient(client);
                accessLog.setProject(project);
                accessLog.setAccessTime(new Timestamp(System.currentTimeMillis()));
                accessLog.setActionType("open");
                projectAccessLogRepository.save(accessLog);

                model.addAttribute("project", project);
                return "edit_project_form";
            } else {
                return "redirect:/userProfile";
            }
        } else {
            return "redirect:/userProfile";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateProject(@PathVariable("id") int projectId,
                                @Valid @ModelAttribute("project") Project projectForm,
                                BindingResult bindingResult,
                                Authentication authentication, Model model) {

        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return "redirect:/login";
        }

        Client client = clientOpt.get();
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return "redirect:/userProfile";
        }

        Project existingProject = projectOpt.get();

        if (!existingProject.getClient().getId().equals(client.getId())) {
            return "redirect:/userProfile";
        }

        projectForm.setClient(client);

        projectValidator.validate(projectForm, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("client", client);
            model.addAttribute("project", projectForm);
            return "edit_project_form";
        }

        existingProject.setName(projectForm.getName());
        existingProject.setLanguage(projectForm.getLanguage());
        existingProject.setReadMe(projectForm.getReadMe());
        existingProject.setRefGit(projectForm.getRefGit());
        existingProject.setProjectType(projectForm.getProjectType());
        existingProject.setAccessLevel(projectForm.getAccessLevel());
        projectService.save(existingProject);

        // Логирование изменений (опционально)
        ProjectAccessLog accessLog = new ProjectAccessLog();
        accessLog.setClient(client);
        accessLog.setProject(existingProject);
        accessLog.setAccessTime(new Timestamp(System.currentTimeMillis()));
        accessLog.setActionType("edit");
        projectAccessLogRepository.save(accessLog);

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

    @PostMapping("/{projectId}/mainClass")
    public ResponseEntity<?> updateMainClass(@PathVariable Integer projectId, @RequestBody Map<String, String> payload) {
        String mainClass = payload.get("mainClass");
        if (mainClass == null || mainClass.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Имя главного класса не может быть пустым");
        }

        Project project = projectService.findById(projectId).orElse(null);
        if (project == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        project.setMainClass(mainClass);
        projectService.save(project);

        return ResponseEntity.ok().body("Имя главного класса обновлено");
    }

    @PostMapping("/{projectId}/save")
    public ResponseEntity<?> saveProjectFiles(
            @PathVariable int projectId,
            @Valid @RequestBody SaveProjectRequest saveRequest,
            Authentication authentication) {

        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();
        if (!projectService.canEditProject(project, client)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        List<FileNodeDto> files = saveRequest.getFiles();
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body("Массив 'files' пуст или отсутствует");
        }

        try {
            projectService.saveProjectFilesFromJson(project, files);
            return ResponseEntity.ok("Проект успешно сохранён");
        } catch (Exception e) {
            logger.error("Ошибка при сохранении проекта", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении проекта");
        }
    }

    @GetMapping("/{projectId}/classes-with-main")
    @ResponseBody
    public ResponseEntity<?> getClassesWithMain(@PathVariable Integer projectId, Authentication authentication) {
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();
        if (!project.getClient().getId().equals(client.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        String language = project.getLanguage().toLowerCase();
        String fileExtension;
        switch (language) {
            case "java":
                fileExtension = ".java";
                break;
            case "python":
                fileExtension = ".py";
                break;
            default:
                return ResponseEntity.badRequest().body("Unsupported language: " + project.getLanguage());
        }

        List<ProjectStruct> filesWithMain = projectStructRepository.findByProjectIdAndType(projectId, "file")
                .stream()
                .filter(ps -> ps.getPath().endsWith(fileExtension))
                .collect(Collectors.toList());

        return ResponseEntity.ok(filesWithMain);
    }
    @GetMapping("/view/{id}")
    public String viewProjectReadOnly(@PathVariable("id") int projectId, Model model, Authentication authentication) {
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return "redirect:/userProfile";
        }

        Project project = projectOpt.get();

        // Проверяем, является ли проект публичным или пользователь имеет доступ
        Client client = null;
        boolean hasAccess = false;
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            client = clientService.findByUsername(authentication.getName()).orElse(null);
            if (client != null) {
                if (project.getAccessLevel() == AccessLevel.PUBLIC) {
                    hasAccess = true;
                } else {
                    // Проверяем, является ли пользователь владельцем или коллабораторами
                    hasAccess = projectService.canEditProject(project, client);
                }
            }
        } else {
            // Если пользователь не авторизован, доступ только к публичным проектам
            if (project.getAccessLevel() == AccessLevel.PUBLIC) {
                hasAccess = true;
            }
        }

        if (!hasAccess) {
            return "redirect:/userProfile"; // Или отображаем страницу ошибки
        }

        // Получаем структуру проекта
        List<JsTreeNodeDto> fileTree = projectService.buildJsTreeFileStructureFromStructs(project, String.valueOf(projectId));
        String fileStructureJson = "[]";
        try {
            fileStructureJson = objectMapper.writeValueAsString(fileTree);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        model.addAttribute("projectName", project.getName());
        model.addAttribute("language", project.getLanguage());
        model.addAttribute("fileStructure", fileStructureJson);
        model.addAttribute("projectId", projectId);

        // Добавляем текущего пользователя, если есть
        model.addAttribute("client", client);

        return "readOnlyProjectView";
    }

    // Endpoint для получения содержимого файла
    @GetMapping("/view/{id}/file-content")
    @ResponseBody
    public ResponseEntity<String> getFileContentReadOnly(@PathVariable("id") int projectId, @RequestParam("path") String path, Authentication authentication) {
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден.");
        }

        Project project = projectOpt.get();

        // Проверяем, является ли проект публичным или пользователь имеет доступ
        Client client = null;
        boolean hasAccess = false;
        if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
            client = clientService.findByUsername(authentication.getName()).orElse(null);
            if (client != null) {
                if (project.getAccessLevel() == AccessLevel.PUBLIC) {
                    hasAccess = true;
                } else {
                    // Проверяем, является ли пользователь владельцем или коллабораторами
                    hasAccess = projectService.canEditProject(project, client);
                }
            }
        } else {
            // Если пользователь не авторизован, доступ только к публичным проектам
            if (project.getAccessLevel() == AccessLevel.PUBLIC) {
                hasAccess = true;
            }
        }

        if (!hasAccess) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Нет доступа к этому проекту.");
        }

        // Получаем содержимое файла из MinIO
        try {
            byte[] contentBytes = minioService.getFileContentAsBytes("projects/" + projectId + "/" + path);
            String content = new String(contentBytes, StandardCharsets.UTF_8);
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            logger.error("Ошибка при получении содержимого файла: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Не удалось получить содержимое файла.");
        }
    }

}