package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.CompilationService;
import com.example.CompilerIDE.services.ProjectService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
//@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;

    public ProjectController(ProjectService projectService, ClientService clientService, CompilationService compilationService) {
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
    }
    // Отображение формы редактирования проекта
    @GetMapping("/projects/edit/{id}")
    public String editProjectForm(@PathVariable("id") int projectId, Model model, Authentication authentication) {
        // Получаем текущего пользователя
        Client client = clientService.findByUsername(authentication.getName()).get();

        // Находим проект по ID
        Optional<Project> projectOpt = projectService.findById(projectId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            // Проверяем, принадлежит ли проект текущему пользователю
            if (project.getClient().getId() == client.getId()) {
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
    @PostMapping("/projects/edit/{id}")
    public String updateProject(@PathVariable("id") int projectId,
                                @Valid @ModelAttribute("project") Project projectForm,
                                BindingResult bindingResult,
                                Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "edit_project_form"; // Возвращаем обратно на форму, если есть ошибки
        }

        Client client = clientService.findByUsername(authentication.getName()).get();
        Optional<Project> projectOpt = projectService.findById(projectId);

        if (projectOpt.isPresent()) {
            Project existingProject = projectOpt.get();
            if (existingProject.getClient().getId() == client.getId()) {
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
    @PostMapping("/{projectId}/save")
    public String saveProjectFiles(@PathVariable int projectId,
                                   @RequestParam("files") List<MultipartFile> files,
                                   @RequestParam(value = "path", defaultValue = "") String path) {
        Optional<Project> project = projectService.findById(projectId);
        if (project.isEmpty()) {
            return "redirect:/error";
        }
        // Сохраняем файлы проекта
        projectService.saveProjectFiles(project.get(), files, path);
        return "redirect:/projects/" + projectId + "/edit";
    }

    @PostMapping("/{projectId}/compile")
    public String compileProject(@PathVariable int projectId, Model model) {
        Optional<Project> project = projectService.findById(projectId);
        if (project.isEmpty()) {
            return "redirect:/error";
        }
        try {
            // Компиляция проекта
            String compilationResult = compilationService.compileProject(project.get());
            model.addAttribute("compilationResult", compilationResult);
            return "compilation_result";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            model.addAttribute("compilationResult", "Ошибка при компиляции проекта.");
            return "compilation_result";
        }
    }

    @PostMapping("/{projectId}/files")
    public String uploadProjectFiles(@PathVariable int projectId,
                                     @RequestParam("files") List<MultipartFile> files,
                                     @RequestParam(value = "path", defaultValue = "") String path) {
        Optional<Project> project = projectService.findById(projectId);
        if (project.isEmpty()) {
            return "redirect:/error";
        }
        projectService.saveProjectFiles(project.orElse(null), files, path);
        return "redirect:/projects/" + projectId;
    }
}
