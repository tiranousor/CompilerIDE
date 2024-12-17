package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.dto.WorkerMetrics;
import com.example.CompilerIDE.providers.*;
import com.example.CompilerIDE.repositories.ClientRepository;
import com.example.CompilerIDE.repositories.LoginTimestampRepository;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.repositories.UnbanRequestRepository;
import com.example.CompilerIDE.services.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final WorkerStatusService workerStatusService;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final MinioService minioService;
    private final ProjectService projectService;
    private final LoginTimestampService loginTimestampService;
    private final UnbanRequestRepository unbanRequestRepository;
    private final ClientService clientService;
    private final UserActivityService userActivityService;
    private final LoginTimestampRepository loginTimestampRepository;

    @Autowired
    public AdminController(WorkerStatusService workerStatusService, ClientRepository clientRepository,
                           ProjectRepository projectRepository, MinioService minioService,
                           ProjectService projectService, LoginTimestampService loginTimestampService,
                           UnbanRequestRepository unbanRequestRepository, ClientService clientService,
                           UserActivityService userActivityService, LoginTimestampRepository loginTimestampRepository) {
        this.workerStatusService = workerStatusService;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.minioService = minioService;
        this.projectService = projectService;
        this.loginTimestampService = loginTimestampService;
        this.unbanRequestRepository = unbanRequestRepository;
        this.clientService = clientService;
        this.userActivityService = userActivityService;
        this.loginTimestampRepository = loginTimestampRepository;
    }


    @GetMapping("/users")
    public String listUsers(@RequestParam(name = "sort", required = false, defaultValue = "registrationDateDesc") String sort,
                            Model model, Authentication authentication) {
        List<Client> users = userActivityService.getUsersSorted(sort);
        model.addAttribute("users", users);
        long totalOnlineTime = userActivityService.calculateTotalOnlineTime();

        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("client", client);
        model.addAttribute("newUsersLastDay", userActivityService.countNewUsersInLastDays(1));
        model.addAttribute("newUsersLastWeek", userActivityService.countNewUsersInLastDays(7));
        model.addAttribute("newUsersLastMonth", userActivityService.countNewUsersInLastDays(30));
        model.addAttribute("bannedUsers", userActivityService.countBannedUsers());
        model.addAttribute("activeUsersLastDay", userActivityService.countActiveUsersInLastDays(1));
        model.addAttribute("activeUsersLastWeek", userActivityService.countActiveUsersInLastDays(7));
        model.addAttribute("activeUsersLastMonth", userActivityService.countActiveUsersInLastDays(30));
        model.addAttribute("topUsers", userActivityService.getTopUsersByOnlineTime(10));

        return "admin/user_list";
    }

    @GetMapping("/status")
    public String getWorkerStatus(Model model,Authentication authentication) {
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("client", client);
        Map<String, Boolean> workerStatus = workerStatusService.getWorkerStatus();
        model.addAttribute("workerStatus", workerStatus);
        return "admin/status";
    }

    @GetMapping("/status/data")
    public ResponseEntity<List<WorkerMetrics>> getWorkerStatusData() {
        List<WorkerMetrics> metricsList = workerStatusService.getAllWorkerMetrics();
        return ResponseEntity.ok(metricsList);
    }
    @GetMapping("/users/list")
    public String getUsersList(@RequestParam(name = "sort", required = false, defaultValue = "registrationDateDesc") String sort, Model model) {
        List<Client> users = userActivityService.getUsersSorted(sort);
        model.addAttribute("users", users);
        return "admin/user_list :: userList";
    }

    @GetMapping("/users/{id}")
    public String viewUserProfile(@PathVariable("id") Long id, Model model, Authentication authentication) {
        Client user = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        List<LoginTimestamp> loginTimestamps = loginTimestampService.findAllByClient(user);
        List<Project> projects = projectRepository.findByClient(user);
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);

        model.addAttribute("user", user);
        model.addAttribute("projects", projects);
        model.addAttribute("loginTimestamps", loginTimestamps);
        model.addAttribute("client", client);

        return "admin/user_profile";
    }

    @PostMapping("/users/{id}/ban")
    public String banUser(@PathVariable("id") Long id) {
        Client user = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        user.setRole("ROLE_BANNED");
        clientRepository.save(user);
        return "redirect:/admin/users";
    }
    @Secured("ROLE_ADMIN")
    @PostMapping("/users/ban")
    @ResponseBody
    public ResponseEntity<?> banUsers(@RequestBody Map<String, List<Long>> payload) {
        List<Long> userIds = payload.get("userIds");
        System.out.println("Received userIds for banning: " + userIds);
        try {
            userActivityService.banUsers(userIds);
            System.out.println("Successfully banned users with IDs: " + userIds);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            System.err.println("Error in banUsers: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error in banUsers: " + e.getMessage());
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }


    @PostMapping("/users/{id}/unban")
    public String unbanUser(@PathVariable("id") Long id) {
        Client user = clientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));
        user.setRole("ROLE_USER");
        clientRepository.save(user);
        return "redirect:/admin/users";
    }

    @GetMapping("/users/search")
    public String searchUsers(@RequestParam("username") String username, Model model) {
        List<Client> users = clientRepository.findByUsernameContainingIgnoreCase(username);
        model.addAttribute("users", users);
        model.addAttribute("username", username);
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

    @PostMapping("/unbanUser/{id}")
    public String unbanUserRequest(@PathVariable("id") Long id) {
        UnbanRequest unbanRequest = unbanRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid unban request Id:" + id));
        Client user = unbanRequest.getClient();
        user.setRole("ROLE_USER");
        clientRepository.save(user);

        unbanRequestRepository.delete(unbanRequest);

        return "redirect:/admin/unbanRequests";
    }

    @PostMapping("/unbanRequests/approve")
    @ResponseBody
    public ResponseEntity<?> approveUnbanRequests(@RequestBody Map<String, List<Long>> payload) {
        List<Long> requestIds = payload.get("requestIds");
        System.out.println("Received requestIds for approval: " + requestIds);
        try {
            userActivityService.approveUnbanRequests(requestIds);
            System.out.println("Successfully approved unban requests: " + requestIds);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            System.err.println("Error in approveUnbanRequests: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error in approveUnbanRequests: " + e.getMessage());
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }
    @PostMapping("/unbanRequests/decline")
    @ResponseBody
    public ResponseEntity<?> declineUnbanRequests(@RequestBody Map<String, List<Long>> payload) {
        List<Long> requestIds = payload.get("requestIds");
        System.out.println("Received requestIds for declination: " + requestIds);
        try {
            userActivityService.declineUnbanRequests(requestIds);
            System.out.println("Successfully declined unban requests: " + requestIds);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            System.err.println("Error in declineUnbanRequests: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error in declineUnbanRequests: " + e.getMessage());
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    @PostMapping("/users/addAdmins")
    @ResponseBody
    public ResponseEntity<?> addAdmins(@RequestBody Map<String, List<Long>> payload) {
        List<Long> userIds = payload.get("userIds");
        System.out.println("Received userIds for adding admins: " + userIds);
        try {
            userActivityService.addAdmins(userIds);
            System.out.println("Successfully added admins for userIds: " + userIds);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            System.err.println("Error in addAdmins: " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error in addAdmins: " + e.getMessage());
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }

    @GetMapping("/dashboard")
    public String getDashboard(Model model, Authentication authentication) {
        List<Client> allClients = clientRepository.findAll();
        List<LoginTimestamp> allTimestamps = loginTimestampRepository.findAll();
        userActivityService.updateDailyStats();
        System.out.println("Accessing /admin/dashboard");
        System.out.println("User: " + authentication.getName());
        System.out.println("Authorities: " + authentication.getAuthorities());

        long totalUsers = userActivityService.countRegisteredUsers();
        long totalProjects = userActivityService.countTotalProjects();
        long totalOnlineTime = userActivityService.calculateTotalOnlineTime();

        List<DailyStats> last7DaysStats = userActivityService.getLast7DaysStats();
        List<String> activityLabels = last7DaysStats.stream()
                .map(stats -> stats.getDate().toString())
                .collect(Collectors.toList());
        List<Long> activityUserCounts = last7DaysStats.stream()
                .map(DailyStats::getUserCount)
                .collect(Collectors.toList());
        List<Long> activityProjectCounts = last7DaysStats.stream()
                .map(DailyStats::getProjectCount)
                .collect(Collectors.toList());

        model.addAttribute("activityLabels", activityLabels);
        model.addAttribute("activityUserCounts", activityUserCounts);
        model.addAttribute("activityProjectCounts", activityProjectCounts);
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("client", client);
        model.addAttribute("newUsersLastDay", userActivityService.countNewUsersInLastDays(1));
        model.addAttribute("newUsersLastWeek", userActivityService.countNewUsersInLastDays(7));
        model.addAttribute("newUsersLastMonth", userActivityService.countNewUsersInLastDays(30));
        model.addAttribute("bannedUsers", userActivityService.countBannedUsers());

        model.addAttribute("activeUsersLastDay", userActivityService.countActiveUsersInLastDays(1));
        model.addAttribute("activeUsersLastWeek", userActivityService.countActiveUsersInLastDays(7));
        model.addAttribute("activeUsersLastMonth", userActivityService.countActiveUsersInLastDays(30));
//        model.addAttribute("topUsers", userActivityService.getTopUsersByOnlineTime(10));
        List<UserActivityService.WeeklyDistribution> weeklyDistribution = userActivityService.getWeeklyDistribution();
        model.addAttribute("weeklyDistribution", weeklyDistribution);
        String formattedTime = formatDuration(totalOnlineTime);
        model.addAttribute("totalOnlineTime", formattedTime);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalProjects", totalProjects);
        List<Map.Entry<Client, Long>> topUsersByOnlineTime = userActivityService.getTopUsersByOnlineTime(3);
        List<ActivityData> topUsers = topUsersByOnlineTime.stream()
                .map(entry -> new ActivityData(entry.getKey().getUsername(), entry.getValue(), entry.getKey().getAvatarUrl()))
                .collect(Collectors.toList());
        model.addAttribute("topUsers", topUsers);
        model.addAttribute("totalProjects", projectService.countTotalProjects());
        model.addAttribute("newProjectsToday", projectService.countNewProjectsInLastDays(1));
        model.addAttribute("newProjectsThisWeek", projectService.countNewProjectsInLastDays(7));
        model.addAttribute("newProjectsThisMonth", projectService.countNewProjectsInLastDays(30));
//        model.addAttribute("activeProjects", projectService.countActiveProjects(7));
//        model.addAttribute("projectLanguageStats", projectService.getProjectLanguageDistribution());
//        model.addAttribute("popularProjects", projectService.getMostPopularProjects(5));

        return "admin/dashboard";
    }
    @Data
    @AllArgsConstructor
    public class ActivityData {
        private String username;
        private Long onlineTime;
        private String formattedOnlineTime;
        private String avatarUrl;
        public ActivityData(String username, Long onlineTime, String avatarUrl) {
            this.username = username;
            this.onlineTime = onlineTime;
            this.formattedOnlineTime = formatDuration(onlineTime);
            this.avatarUrl = avatarUrl != null ? avatarUrl : "/noAvatar.png";
        }
    }

    @GetMapping("/totalOnlineTime")
    @ResponseBody
    public String getTotalOnlineTime() {
        long totalOnlineSeconds = userActivityService.calculateTotalOnlineTime();
        return formatDuration(totalOnlineSeconds); // Возвращаем форматированное время
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }

    @GetMapping("/projects_list")
    public String listAllProjects( Model model, Authentication authentication) {
        List<Project> allProjects = projectService.findAllProjects();
        model.addAttribute("projects", allProjects);
        model.addAttribute("newProjectsToday", projectService.countNewProjectsInLastDays(1));
        model.addAttribute("newProjectsThisWeek", projectService.countNewProjectsInLastDays(7));
        model.addAttribute("newProjectsThisMonth", projectService.countNewProjectsInLastDays(30));
        Client client = clientService.findByUsername(authentication.getName()).orElse(null);
        long totalProjects = userActivityService.countTotalProjects();
        model.addAttribute("totalProjects", totalProjects);
        model.addAttribute("client", client);
        return "admin/projects_list";
    }
}
