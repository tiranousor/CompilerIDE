package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.JsTreeNodeDto;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.MinioService;
import com.example.CompilerIDE.services.ProjectService;
import com.example.CompilerIDE.util.ClientValidator;
import com.example.CompilerIDE.util.FileUploadUtil;
import com.example.CompilerIDE.util.ProjectValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Controller
public class UserController {
    private final ClientService clientService;
    private final ProjectService projectService;
    private final ClientValidator clientValidator;
    private final ProjectValidator projectValidator;
    private final MinioService minioService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserController(ClientService clientService, ProjectService projectService, ClientValidator clientValidator,
                          ProjectValidator projectValidator, MinioService minioService,
                          ObjectMapper objectMapper) {
        this.clientService = clientService;
        this.clientValidator = clientValidator;
        this.projectService = projectService;
        this.projectValidator = projectValidator;
        this.minioService = minioService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String home() {
        return "CompilerHomepage";
    }

    @GetMapping("Compiler/project/{projectId}")
    public String compiler(@PathVariable("projectId") int projectId, Authentication authentication, Model model) {
        Project project = projectService.findById(projectId).orElse(null);
        if (project == null) {
            return "redirect:/userProfile";
        }

        if (!project.getClient().getUsername().equals(authentication.getName())) {
            return "redirect:/userProfile";
        }

        model.addAttribute("projectId", projectId);
        System.out.println(projectId);
        List<String> filePaths = minioService.listFiles("projects/" + projectId + "/");

        if (filePaths == null) {
            filePaths = List.of();
        }

        List<JsTreeNodeDto> fileTree = projectService.buildJsTreeFileStructureFromStructs(project, String.valueOf(projectId));
        String fileStructureJson = "[]";

        try {
            fileStructureJson = objectMapper.writeValueAsString(fileTree);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        model.addAttribute("fileStructure", fileStructureJson);
        System.out.println(fileStructureJson);
        return "Compiler2";
    }

    @GetMapping("/login")
    public String showLoginPage() {
        return "loginAndRegistration";
    }

    @PostMapping("/login")
    public String processLogin(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/userProfile";
        }
        return "loginAndRegistration";
    }

    @GetMapping("/registration")
    public String registration(@ModelAttribute Client client){
        return "registrationPage";
    }

    @PostMapping("/process_registration")
    public String registrationPerson(@Valid @ModelAttribute Client client, BindingResult bindingResult) {
        clientValidator.validate(client, bindingResult);

        if (bindingResult.hasErrors())
            return "registrationPage";

        clientService.save(client);

        Project project = new Project();
        project.setName("Untitled");
        project.setLanguage("Java");
       project.setClient(client);

        projectService.save(project);

        return "redirect:/login?registration";
    }

    @GetMapping("/userProfile/new")
    public String newProjectForm(Authentication authentication, Model model) {
        model.addAttribute("project", new Project());
        return "new_project_form";
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

        return "redirect:/userProfile";
    }


    @PostMapping("/userProfile/delete/{id}")
    public String deleteProject(@PathVariable("id") int projectId, Authentication authentication) {
        Client client = clientService.findByUsername(authentication.getName()).get();

        Optional<Project> projectToDelete = projectService.findById(projectId);

        if (projectToDelete.isPresent() && projectToDelete.get().getClient().getId() == client.getId()) {
            projectService.delete(projectToDelete.get());
        }

        return "redirect:/userProfile";
    }

    @GetMapping("/userProfile")
    public String ClientProfile(Model model, Authentication authentication) {
        Client client = clientService.findByUsername(authentication.getName()).get();

        List<Project> project = projectService.findByClient(client);
        model.addAttribute("client", client);
        model.addAttribute("projects", project);

        return "userProfile";
    }

    @GetMapping("/edit/{id}")
    public String editProfile(Model model, @PathVariable("id") int id) {
        model.addAttribute("client", clientService.findOne(id));
        return "editProfile";
    }

    @PostMapping("/edit/{id}")
    public String updateProfile(Authentication authentication, @PathVariable("id") int id,
                                @Valid Client clientForm, BindingResult bindingResult,
                                @RequestParam("avatarFile") MultipartFile avatarFile) {
        if (bindingResult.hasErrors()) {
            return "editProfile";
        }

        Client existingClient = clientService.findOne(id);

        if (!avatarFile.isEmpty()) {
            String originalFileName = avatarFile.getOriginalFilename();
            String extension = "";

            if (originalFileName != null) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }

            String fileName = existingClient.getId() + extension;
            String uploadDir = "user-photos/";
            try {
                FileUploadUtil.saveFile(uploadDir, fileName, avatarFile);
                existingClient.setAvatarUrl("/" + uploadDir + fileName);
            } catch (IOException e) {
                bindingResult.rejectValue("avatarUrl","error.avatarUrl", e.getMessage());
                return "editProfile";
            }
        }

        existingClient.setUsername(clientForm.getUsername());
        existingClient.setEmail(clientForm.getEmail());
        existingClient.setGithubProfile(clientForm.getGithubProfile());
        existingClient.setAbout(clientForm.getAbout());
        clientService.update(id, existingClient);

        if (!authentication.getName().equals(existingClient.getUsername())) {
            Authentication newAuth = new UsernamePasswordAuthenticationToken(existingClient.getUsername(),
                    authentication.getCredentials(), authentication.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(newAuth);
        }

        return "redirect:/userProfile";
    }
}