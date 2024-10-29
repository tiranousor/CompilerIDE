package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.LoginTimestamp;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.repositories.LoginTimestampRepository;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.services.LoginTimestampService;
import com.example.CompilerIDE.services.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin")
@Secured("ROLE_ADMIN") // Ограничиваем доступ к контроллеру только для администраторов
public class AdminController {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final MinioService minioService;
    private final LoginTimestampService loginTimestampService;

    @Autowired
    public AdminController(ClientRepository clientRepository, ProjectRepository projectRepository, MinioService minioService, LoginTimestampService loginTimestampService) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.minioService = minioService;
        this.loginTimestampService = loginTimestampService;
    }

    // Просмотр всех пользователей
    @GetMapping("/users")
    public String listUsers(Model model) {
        List<Client> users = clientRepository.findAll();
        model.addAttribute("users", users);
        return "admin/user_list";
    }

    // Просмотр профиля пользователя по ID
    @GetMapping("/users/{id}")
    public String viewUserProfile(@PathVariable("id") Integer id, Model model) {
        Client user = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        List<Project> projects = projectRepository.findByClient(user);
        model.addAttribute("user", user);
        model.addAttribute("projects", projects);
        List<LoginTimestamp> loginTimestamps = loginTimestampService.findAllByClient(user);
        model.addAttribute("loginTimestamps", loginTimestamps);
        return "admin/user_profile";
    }

    // Бан пользователя
    @PostMapping("/users/{id}/ban")
    public String banUser(@PathVariable("id") Integer id) {
        Client user = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        user.setRole("ROLE_BANNED");
        clientRepository.save(user);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/unban")
    public String unbanUser(@PathVariable("id") Integer id) {
        Client user = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        user.setRole("ROLE_USER");
        clientRepository.save(user);
        return "redirect:/admin/users";
    }

    // Просмотр использования памяти в MinIO
    @GetMapping("/storage-usage")
    public String getStorageUsage(Model model) {
        long totalUsedSpace = minioService.getTotalUsedSpace(); // Получаем общую информацию о занятой памяти
        model.addAttribute("usedSpace", totalUsedSpace);
        return "admin/storage_usage";
    }
    @GetMapping("/users/search")
    public String searchUsers(@RequestParam("username") String username, Model model) {
        List<Client> users = clientRepository.findByUsernameContainingIgnoreCase(username);
        model.addAttribute("users", users);
        model.addAttribute("username", username); // Передаем обратно, чтобы сохранить значение в поле поиска
        return "admin/user_list";
    }
}
