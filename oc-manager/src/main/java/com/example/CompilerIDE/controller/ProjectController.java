package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.ProjectService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;

    public ProjectController(ProjectService projectService, ClientService clientService) {
        this.projectService = projectService;
        this.clientService = clientService;
    }

    // Отображение всех проектов клиента
//    @GetMapping("/userProfile")
//    public String showProjects(@PathVariable int clientId, Model model) {
//        Client client = clientService.findOne(clientId);
//        model.addAttribute("client", client);
//        model.addAttribute("projects", projectService.findByClient(client));
//        return "userProfile"; // это наш HTML файл профиля
//    }
//
//    @GetMapping("/userProfile/new")
//    public String newProjectForm(Authentication authentication, Model model) {
////        model.addAttribute("clientId", clientService.getPerson(authentication.getName()).get().getId());
//        model.addAttribute("project", new Project());
//        return "new_project_form"; // это HTML форма для добавления проекта
//    }
//
//    @PostMapping("/userProfile/new")
//    public String createProject(@ModelAttribute Project project, Authentication authentication) {
//        project.setClient(clientService.getClient(authentication.getName()).get());
//        projectService.save(project);
//        return "redirect:/projects/"; // перенаправляем обратно на страницу проектов
//    }

}
