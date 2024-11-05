package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.FileNodeDto;
import com.example.CompilerIDE.Dto.SaveProjectRequest;
import com.example.CompilerIDE.providers.*;
import com.example.CompilerIDE.repositories.ProjectAccessLogRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.services.*;
import com.example.CompilerIDE.util.ProjectValidator;
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
    private final ProjectInvitationService projectInvitationService;
    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;
    private final MinioService minioService; // Добавлено
    private final ProjectStructRepository projectStructRepository;
    private final ProjectTeamService projectTeamService;
    private final ProjectValidator projectValidator;

    private final ProjectAccessLogRepository projectAccessLogRepository;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    @Value("${minio.bucket-name}")
    private String bucketName; // Убедитесь, что бакет создан

    public ProjectController(ProjectInvitationService projectInvitationService, ProjectService projectService,
                             ClientService clientService,
                             CompilationService compilationService,
                             MinioService minioService, ProjectStructRepository projectStructRepository, ProjectTeamService projectTeamService, ProjectValidator projectValidator, ProjectAccessLogRepository projectAccessLogRepository) {
        this.projectInvitationService = projectInvitationService; // Добавлено
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
        this.minioService = minioService; // Добавлено
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

        return "projectInfo"; // Убедитесь, что у вас есть соответствующий шаблон
    }
    @GetMapping("/userProfile/new")
    public String newProjectForm(Authentication authentication, Model model) {
        model.addAttribute("project", new Project());
        return "new_project_form"; // Ensure this template exists
    }

    @PostMapping("/userProfile/new")
    public String createProject(@Valid @ModelAttribute("project") Project project, BindingResult bindingResult, Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "new_project_form";
        }
        project.setClient(clientService.getClient(authentication.getName()).get());
        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            return "new_project_form";
        }
        projectService.save(project);
//        projectTeamService.addCreator(project, clientService.getClient(authentication.getName()).get());
        return "redirect:/userProfile";
    }


    // Delete a project
    @PostMapping("/userProfile/delete/{id}")
    public String deleteProject(@PathVariable("id") int projectId, Authentication authentication) {
        Client client = clientService.findByUsername(authentication.getName()).get();
        Optional<Project> projectToDelete = projectService.findById(projectId);

        if (projectToDelete.isPresent() && projectToDelete.get().getClient().getId() == client.getId()) {
            projectService.delete(projectToDelete.get()); // Delete the project from DB
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
        if (!project.getClient().getId().equals(client.getId())) {
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
    public boolean canEditProject(Project project, Client client) {
        Optional<ProjectTeam> team = projectTeamService.findByProjectAndClient(project, client);
        return team.isPresent() && (team.get().getRole() == ProjectTeam.Role.CREATOR || team.get().getRole() == ProjectTeam.Role.COLLABORATOR);
    }


}