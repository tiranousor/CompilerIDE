package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.Dto.ClientDto;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.MinioService;
import com.example.CompilerIDE.services.ProjectService;
import com.example.CompilerIDE.util.ClientValidator;
import com.example.CompilerIDE.util.FileUploadUtil;
import com.example.CompilerIDE.util.ProjectValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
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
    private final PasswordEncoder passwordEncoder;
    private final ProjectValidator projectValidator;
    private final MinioService minioService;

    @Autowired
    public UserController(ClientService clientService, ProjectService projectService, ClientValidator clientValidator,
                          PasswordEncoder passwordEncoder, ProjectValidator projectValidator, MinioService minioService) {
        this.passwordEncoder = passwordEncoder;
        this.clientService = clientService;
        this.clientValidator = clientValidator;
        this.projectService = projectService;
        this.projectValidator = projectValidator;
        this.minioService = minioService;
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

        // Получаем список полных путей файлов из базы данных
        List<String> filePaths = projectService.getFilePaths(project);

        if (filePaths == null) {
            filePaths = new ArrayList<>();
        }
        model.addAttribute("files", filePaths);

        return "Compiler";
    }


    @GetMapping("/login")
    public String showLoginPage() {
        return "loginAndRegistration";
    }
    @PostMapping("/login")
    public String processLogin(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            // Получаем текущего пользователя
            String username = authentication.getName();
            Optional<Client> client = clientService.findByUsername(username);

            // Обновляем время последнего входа и добавляем в список всех входов
            client.ifPresent(c -> {
                Date now = new Date();
                c.setLastLoginTime(now);  // для проверки активности
                c.getLoginTimes().add(now);  // добавляем метку входа в историю
                clientService.update(c.getId(), c);
            });

            return "redirect:/userProfile";
        }
        return "loginAndRegistration";
    }
    @GetMapping("/userProfile/activity")
    public String showUserActivity(Model model, Authentication authentication) {
        Optional<Client> client = clientService.findByUsername(authentication.getName());
        if (client.isPresent()) {
            model.addAttribute("loginTimes", client.get().getLoginTimes()); // передаем список временных меток
        }
        return "userActivity"; // возвращаем шаблон
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

        // Сохраняем проект
        projectService.save(project);

        return "redirect:/login?registration";
    }

    @GetMapping("/userProfile/new")
    public String newProjectForm(Authentication authentication, Model model) {
        model.addAttribute("project", new Project());
        return "new_project_form"; // Ensure this template exists
    }

    @PostMapping("/userProfile/new")
    public String createProject(@Valid @ModelAttribute("project") Project project, BindingResult bindingResult, Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "new_project_form"; // Возвращаем обратно на форму при ошибках
        }
        project.setClient(clientService.getClient(authentication.getName()).get());
        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            return "new_project_form"; // Возвращаем обратно на форму при ошибках
        }
        projectService.save(project);

        return "redirect:/userProfile";
    }


    // Delete a project
    @PostMapping("/userProfile/delete/{id}")
    public String deleteProject(@PathVariable("id") int projectId, Authentication authentication) {
        // Get the logged-in user
        Client client = clientService.findByUsername(authentication.getName()).get();

        // Find the project to delete
        Optional<Project> projectToDelete = projectService.findById(projectId);

        // Ensure the project belongs to the current user
        if (projectToDelete.isPresent() && projectToDelete.get().getClient().getId() == client.getId()) {
            projectService.delete(projectToDelete.get()); // Delete the project from DB
        }

        // Redirect back to the user's profile page after deletion
        return "redirect:/userProfile";
    }

//    @GetMapping("/userProfile")
//    public String ClientProfile(Model model, Authentication authentication) {
//        Client client = clientService.findByUsername(authentication.getName()).get();
//
//        List<Project> project = projectService.findByClient(client);
//        model.addAttribute("client", client);
//        model.addAttribute("projects", project);
//
//        return "userProfile";
//    }
@GetMapping("/userProfile")
public String ClientProfile(Model model, Authentication authentication) {
    Client client = clientService.findByUsername(authentication.getName()).orElse(null);
    if (client == null) {
        return "redirect:/login";
    }

    Date currentTime = new Date();
    boolean isActive = client.getLastLoginTime() != null &&
            (currentTime.getTime() - client.getLastLoginTime().getTime()) <= (10 * 60 * 1000);

    model.addAttribute("client", client);
    model.addAttribute("projects", projectService.findByClient(client));
    model.addAttribute("isActive", isActive);  // передаем параметр активности

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

//        if (!clientForm.getPassword().isEmpty()) {
//            existingClient.setPassword(passwordEncoder.encode(clientForm.getPassword()));
//        }


        clientService.update(id, existingClient);

        if (!authentication.getName().equals(existingClient.getUsername())) {
            Authentication newAuth = new UsernamePasswordAuthenticationToken(existingClient.getUsername(),
                    authentication.getCredentials(), authentication.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(newAuth);
        }

        return "redirect:/userProfile";
    }

//    @PostMapping("/edit/{id}")
//    public String updateProfile(Authentication authentication, @PathVariable("id") int id,@ModelAttribute("client") @Valid ClientDto clientDto, BindingResult bindingResult) {
//        if (bindingResult.hasErrors()) {
//            return "editProfile";
//        }
//        Client client = clientService.findOne(id);
//        client = clientMapper.updateUserFromDto(clientDto,client);
//        clientService.update(client);
//        if (!authentication.getName().equals(client.getUsername())) {
//            Authentication newAuth = new UsernamePasswordAuthenticationToken(client.getUsername(), authentication.getCredentials(), authentication.getAuthorities());
//            SecurityContextHolder.getContext().setAuthentication(newAuth);
//        }
//
//        return "redirect:/userProfile";
//    }



}