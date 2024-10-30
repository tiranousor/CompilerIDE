package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.CompileRequest;
import com.example.CompilerIDE.Dto.FileNodeDto;
import com.example.CompilerIDE.Dto.SaveProjectRequest;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.CompilationService;
import com.example.CompilerIDE.services.ProjectService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    public ProjectController(ProjectService projectService,
                             ClientService clientService,
                             CompilationService compilationService) {
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
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
            if (project.getClient().getId().equals(client.getId())) {
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
            }
        }
        return "redirect:/userProfile";
    }

    @PostMapping("/{projectId}/compile")
    public ResponseEntity<?> compileProject(@PathVariable int projectId,
                                            @RequestBody CompileRequest compileRequest,
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

        try {
            String compilationResult = compilationService.compileProject(project);

            return ResponseEntity.ok(compilationResult);
        } catch (IOException | InterruptedException e) {
            logger.error("Error compiling project", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при компиляции проекта");
        }
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

}
