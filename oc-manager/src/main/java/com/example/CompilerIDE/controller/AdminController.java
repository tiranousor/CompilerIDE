package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.LoginTimestamp;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.UnbanRequest;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.repositories.LoginTimestampRepository;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.repositories.UnbanRequestRepository;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.LoginTimestampService;
import com.example.CompilerIDE.services.MinioService;
import com.example.CompilerIDE.services.WorkerStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final WorkerStatusService workerStatusService;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final MinioService minioService;
    private final LoginTimestampService loginTimestampService;
    private final UnbanRequestRepository unbanRequestRepository;
    private final ClientService clientService;
    @Autowired
    public AdminController(WorkerStatusService workerStatusService, ClientRepository clientRepository, ProjectRepository projectRepository, MinioService minioService, LoginTimestampService loginTimestampService, UnbanRequestRepository unbanRequestRepository, ClientService clientService) {
        this.workerStatusService = workerStatusService;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.minioService = minioService;
        this.loginTimestampService = loginTimestampService;
        this.unbanRequestRepository = unbanRequestRepository;
        this.clientService = clientService;
    }
    @GetMapping("/status")
    public String getStatus(Model model) {
        Map<String, Boolean> workerStatus = workerStatusService.getWorkerStatus();
        model.addAttribute("workerStatus", workerStatus);
        return "admin/status";
    }
//    @Secured("ADMIN")
    @GetMapping("/users")
    public String listUsers(Model model, Authentication authentication) {
        List<Client> users = clientRepository.findAll();
        model.addAttribute("users", users);
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("client", client);

        return "admin/user_list";
    }

    // Просмотр профиля пользователя по ID
    @GetMapping("/users/{id}")
    public String viewUserProfile(@PathVariable("id") Integer id, Model model, Authentication authentication) {
        Client user = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        List<Project> projects = projectRepository.findByClient(user);
        model.addAttribute("user", user);
        model.addAttribute("projects", projects);
        List<LoginTimestamp> loginTimestamps = loginTimestampService.findAllByClient(user);
        model.addAttribute("loginTimestamps", loginTimestamps);
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("client", client);
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
//    @GetMapping("/storage-usage")
//    public String getStorageUsage(Model model) {
//        long totalUsedSpace = minioService.getTotalUsedSpace(); // Получаем общую информацию о занятой памяти
//        model.addAttribute("usedSpace", totalUsedSpace);
//        return "admin/storage_usage";
//    }
    @GetMapping("/users/search")
    public String searchUsers(@RequestParam("username") String username, Model model) {
        List<Client> users = clientRepository.findByUsernameContainingIgnoreCase(username);
        model.addAttribute("users", users);
        model.addAttribute("username", username); // Передаем обратно, чтобы сохранить значение в поле поиска
        return "admin/user_list";
    }
    @GetMapping("/unbanRequests")
    public String listUnbanRequests(Model model, Authentication authentication) {
        List<UnbanRequest> unbanRequests = unbanRequestRepository.findAll();
        model.addAttribute("unbanRequests", unbanRequests);

        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("client", client);
        return "admin/unban_requests";


    }

    @Secured("ROLE_ADMIN")
    @PostMapping("/unbanUser/{id}")
    public String unbanUser(@PathVariable("id") Long id) {
        UnbanRequest unbanRequest = unbanRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid unban request Id:" + id));
        Client user = unbanRequest.getClient();
        user.setRole("ROLE_USER");
        clientRepository.save(user);

        // Удаляем запрос на разблокировку
        unbanRequestRepository.delete(unbanRequest);

        return "redirect:/admin/unbanRequests";
    }

    @Secured("ROLE_ADMIN")
    @PostMapping("/declineUnbanRequest/{id}")
    public String declineUnbanRequest(@PathVariable("id") Long id) {
        UnbanRequest unbanRequest = unbanRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid unban request Id:" + id));
        // Удаляем запрос без разблокировки пользователя
        unbanRequestRepository.delete(unbanRequest);

        return "redirect:/admin/unbanRequests";
    }
}
