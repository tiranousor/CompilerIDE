package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.dto.JsTreeNodeDto;
import com.example.CompilerIDE.providers.*;
import com.example.CompilerIDE.repositories.LoginTimestampRepository;
import com.example.CompilerIDE.security.ClientDetails;
import com.example.CompilerIDE.services.*;
import com.example.CompilerIDE.util.ClientValidator;
import com.example.CompilerIDE.util.FileUploadUtil;
import com.example.CompilerIDE.util.ProjectValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

@Controller
public class UserController {
    private final ProjectAccessLogService projectAccessLogService;
    private final ClientService clientService;
    private final ProjectService projectService;
    private final ProjectInvitationService projectInvitationService;
    private final ClientValidator clientValidator;
    private final PasswordEncoder passwordEncoder;
    private final ProjectValidator projectValidator;
    private final MinioService minioService;
    private final ObjectMapper objectMapper;
    private final FriendshipService friendshipService;
    private final LoginTimestampRepository loginTimestampRepository;
    private final ProjectTeamService projectTeamService;
    private  final  FriendRequestService friendRequestService;
    @Autowired
    public UserController(ProjectAccessLogService projectAccessLogService, ClientService clientService, ProjectService projectService, ProjectInvitationService projectInvitationService, ClientValidator clientValidator,
                          PasswordEncoder passwordEncoder, ProjectValidator projectValidator, MinioService minioService,
                          ObjectMapper objectMapper, FriendshipService friendshipService,
                          LoginTimestampRepository loginTimestampRepository, ProjectTeamService projectTeamService, FriendRequestService friendRequestService) {
        this.projectAccessLogService = projectAccessLogService;
        this.projectInvitationService = projectInvitationService;
        this.passwordEncoder = passwordEncoder;
        this.clientService = clientService;
        this.clientValidator = clientValidator;
        this.projectService = projectService;
        this.projectValidator = projectValidator;
        this.minioService = minioService;
        this.objectMapper = objectMapper;
        this.friendshipService = friendshipService;
        this.loginTimestampRepository = loginTimestampRepository;
        this.projectTeamService = projectTeamService;
        this.friendRequestService = friendRequestService;
    }

    @GetMapping("/")
    public String home() {
        return "CompilerHomepage";
    }

    @GetMapping("Compiler/project/{projectId}")
    public String compiler(@PathVariable("projectId") int projectId, Authentication authentication, Model model) {
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return "redirect:/userProfile";
        }

        Project project = projectOpt.get();
        Client currentUser = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + authentication.getName()));
        boolean isOwner = project.getClient().getId().equals(currentUser.getId());
        boolean isCollaborator = projectTeamService.findByProjectAndClient(project, currentUser)
                .map(team -> team.getRole() == ProjectTeam.Role.COLLABORATOR)
                .orElse(false);

        if (!isOwner && !isCollaborator) {
            return "redirect:/userProfile";
        }

        // Логирование доступа
        projectAccessLogService.logAccess(currentUser, project, "view");

        model.addAttribute("projectId", projectId);
        model.addAttribute("mainClass", project.getMainClass());

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
        model.addAttribute("projectName", project.getName());
        model.addAttribute("language", project.getLanguage());
        model.addAttribute("fileStructure", fileStructureJson);

        return "Compiler";
    }


    @GetMapping("/login")
    public String showLoginPage() {
        return "loginAndRegistration";
    }


    @GetMapping("/registration")
    public String registration(@ModelAttribute Client client) {
        return "registrationPage";
    }


    @PostMapping("/process_registration")
    public String registrationPerson(@Valid @ModelAttribute("client") Client client, BindingResult bindingResult) {
        clientValidator.validate(client, bindingResult);

        if (bindingResult.hasErrors())
            return "registrationPage";

        clientService.save(client);

        Project project = new Project();
        project.setName("Untitled");
        project.setLanguage("Java");
        project.setReadMe("Мой первый проект");
        project.setClient(client);
        project.setDashboardUid("de6askgqns3k0f");
        projectService.save(project);
        projectTeamService.addCreator(project, client);
        return "redirect:/login?registration";
    }


    @GetMapping("/userProfile")
    public String ClientProfile(Model model, Authentication authentication) {
        String authName = authentication.getName();
        System.out.println("Authenticated Username: " + authName);

        Optional<Client> clientOpt = clientService.findByUsername(authName);
//        if (clientOpt.isEmpty()) {
//            return "redirect:/login";
//        }
        Client client = clientOpt.get();
        List<ProjectInvitation> pendingInvitations = projectInvitationService.getPendingInvitationsForReceiver(client);
        int invitationCount = pendingInvitations.size();
        model.addAttribute("invitationCount", invitationCount);
        List<Project> projects = projectService.findByClient(client);
        List<Client> friends = friendshipService.getFriends(client);
        List<ProjectTeam> collaboratorProjects = projectTeamService.findCollaboratorProjects(client);
        model.addAttribute("client", client);
        model.addAttribute("projects", projects);
        model.addAttribute("friendsCount", friends.size());
        model.addAttribute("friends", friends);

        model.addAttribute("collaboratorProjects", collaboratorProjects);
        return "userProfile";
    }

    @GetMapping("/edit/{id}")
    public String editProfile(Model model, @PathVariable("id") int id, Authentication authentication) {
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return "redirect:/userProfile/{id}";
        }
        Client client = clientOpt.get();
        model.addAttribute("client", clientService.findOne(client.getId()));
        return "editProfile";
    }

    @PostMapping("/edit/{id}")
    public String updateProfile(Authentication authentication, @PathVariable("id") int id,
                                @Valid @ModelAttribute("client") Client clientForm, BindingResult bindingResult,
                                @RequestParam("avatarFile") MultipartFile avatarFile) {

        if (!avatarFile.isEmpty() && avatarFile.getSize() > (2 * 1024 * 1024)) {
            bindingResult.rejectValue("avatarUrl", "error.avatarUrl", "Размер файла превышает допустимый лимит (2MB). Пожалуйста, выберите файл меньшего размера.");
            return "editProfile";
        }
        if (bindingResult.hasErrors()) {
            return "editProfile";
        }

        Client existingClient = clientService.findOne(id);
        if (existingClient == null) {
            bindingResult.reject("client.notfound", "Клиент не найден");
            return "editProfile";
        }

        if (!avatarFile.isEmpty()) {
            String originalFileName = avatarFile.getOriginalFilename();
            String extension = "";

            if (originalFileName != null && originalFileName.contains(".")) {
                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }

            String fileName = existingClient.getId() + extension;
            String uploadDir = "user-photos/";
            try {
                FileUploadUtil.saveFile(uploadDir, fileName, avatarFile);
                existingClient.setAvatarUrl("/" + uploadDir + fileName);
            } catch (IOException e) {
                bindingResult.rejectValue("avatarUrl", "error.avatarUrl", "Ошибка при загрузке файла: " + e.getMessage());
                return "editProfile";
            }
        }

        existingClient.setUsername(clientForm.getUsername());
        existingClient.setEmail(clientForm.getEmail());
        existingClient.setGithubProfile(clientForm.getGithubProfile());
        existingClient.setAbout(clientForm.getAbout());

        clientService.update(id, existingClient);

        ClientDetails updatedClientDetails = new ClientDetails(existingClient);

        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                updatedClientDetails,
                authentication.getCredentials(),
                updatedClientDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(newAuth);


        return "redirect:/userProfile/" + existingClient.getId();

    }

    @GetMapping("/userProfile/{id}")
    public String viewUserProfile(@PathVariable("id") int id, Model model, Authentication authentication) {

        String authName = authentication.getName();
        Optional<Client> currentUserOpt = clientService.findByUsername(authName);
        if (currentUserOpt.isEmpty()) {
            return "redirect:/login";
        }
        Client currentUser = currentUserOpt.get();
        Client viewedUser = clientService.findOne(id);
        if (viewedUser == null) {
            return "redirect:/userProfile/" + currentUser.getId();
        }
        List<FriendRequest> receivedRequests = friendRequestService.getPendingReceivedRequests(currentUser);
        model.addAttribute("receivedRequests", receivedRequests);

        boolean isOwnProfile = viewedUser.getId().equals(currentUser.getId());
        model.addAttribute("isOwnProfile", isOwnProfile);

        List<ProjectTeam> collaboratorProjects = projectTeamService.findCollaboratorProjects(currentUser);
        List<ProjectTeam> projectTeams = projectTeamService.findByClient(viewedUser);
        model.addAttribute("collaboratorProjects", collaboratorProjects);
        List<ProjectInvitation> pendingInvitations = projectInvitationService.getPendingInvitationsForReceiver(currentUser);
        int invitationCount = pendingInvitations.size();
        model.addAttribute("invitationCount", invitationCount);
        model.addAttribute("client", viewedUser);
        model.addAttribute("friendsCount", friendshipService.getFriends(viewedUser).size());
        model.addAttribute("collaboratorProjects", collaboratorProjects);
        if (isOwnProfile) {
            model.addAttribute("projects", projectService.findByClient(viewedUser));
            model.addAttribute("friends", friendshipService.getFriends(viewedUser));
            model.addAttribute("friendsProjects", projectTeamService.findCollaboratorProjects(currentUser));
        } else {
            model.addAttribute("projects", projectService.findAccessibleProjects(viewedUser, currentUser));
            model.addAttribute("friends", friendshipService.getFriends(viewedUser));
        }

        return "userProfile";
    }

    @GetMapping("/monitoring")
    public String monitoringPage(Authentication authentication, Model model) {
        Client currentUser = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<Project> ownedProjects = projectService.findByClient(currentUser);
        if (ownedProjects.isEmpty()) {
            model.addAttribute("message", "У вас нет созданных проектов для мониторинга.");
            return "monitoring";
        }

        Map<String, String> projectDashboards = new HashMap<>();
        for (Project project : ownedProjects) {
            projectDashboards.put(project.getName(), project.getDashboardUid());
        }

        model.addAttribute("projectDashboards", projectDashboards);
        model.addAttribute("ownedProjects", ownedProjects);

        return "monitoring";
    }
    @GetMapping("/check_email")
    @ResponseBody
    public Map<String, Boolean> checkEmail(@RequestParam("email") String email) {
        boolean exists = clientService.getClientByEmail(email).isPresent();
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return response;
    }
    @GetMapping("/check_username")
    @ResponseBody
    public Map<String, Boolean> checkUsername(@RequestParam("username") String username) {
        boolean exists = clientService.findByUsername(username).isPresent();
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return response;
    }
    @GetMapping("/searchUsers")
    @ResponseBody
    public List<Map<String, Object>> searchUsers(@RequestParam("query") String query, Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Client> matchingUsers = clientService.searchByUsername(query);
        List<Client> friends = friendshipService.getFriends(currentUser);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Client user : matchingUsers) {
            if (user.equals(currentUser)) continue; // Exclude the current user

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("username", user.getUsername());
            userMap.put("avatarUrl", user.getAvatarUrl());
            userMap.put("isFriend", friends.contains(user)); // Check friendship status
            results.add(userMap);
        }

        return results;
    }


}