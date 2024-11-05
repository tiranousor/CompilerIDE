package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.FriendRequest;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectTeam;
import com.example.CompilerIDE.services.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/friends")
public class FriendController {

    private final ClientService clientService;
    private final FriendRequestService friendRequestService;
    private final FriendshipService friendshipService;
    private final ProjectService projectService;
    private final ProjectInvitationService projectInvitationService;
    private final ProjectTeamService projectTeamService;
    @Autowired
    public FriendController(ClientService clientService, FriendRequestService friendRequestService, FriendshipService friendshipService, ProjectService projectService, ProjectInvitationService projectInvitationService, ProjectTeamService projectTeamService) {
        this.clientService = clientService;
        this.friendRequestService = friendRequestService;
        this.friendshipService = friendshipService;
        this.projectService = projectService;
        this.projectInvitationService = projectInvitationService;
        this.projectTeamService = projectTeamService;
    }


    @GetMapping("/search")
    public String searchUsers(@RequestParam(value = "username", required = false) String username, Model model, Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);

        if (username == null || username.trim().isEmpty()) {
            // No search parameter provided; display the search form
            return "searchUsers";
        }

        // Perform the search
        List<Client> users = clientService.findByUsernameContainingIgnoreCase(username);

        // Exclude the current user from the search results
        if (currentUser != null) {
            users = users.stream()
                    .filter(user -> !user.equals(currentUser))
                    .collect(Collectors.toList());
        }

        model.addAttribute("users", users);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("searchQuery", username);

        return "searchUsers";
    }

    /**
     * Send a friend request to a user.
     */
    @PostMapping("/sendRequest/{receiverId}")
    @ResponseBody
    public String sendFriendRequest(@PathVariable("receiverId") Integer receiverId, Authentication authentication) {
        Client sender = clientService.findByUsername(authentication.getName()).orElse(null);
        Client receiver = clientService.findOne(receiverId);

        if (sender == null || receiver == null) {
            return "User not found.";
        }

        try {
            friendRequestService.sendFriendRequest(sender, receiver);
            return "success";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * View incoming friend requests.
     */
    @GetMapping("/requests")
    public String viewFriendRequests(Model model, Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);
        List<FriendRequest> receivedRequests = friendRequestService.getPendingReceivedRequests(currentUser);
        model.addAttribute("receivedRequests", receivedRequests);
        return "friendRequests";
    }
    @GetMapping("/list")
    public String viewFriendsAndRequests(@RequestParam(value = "username", required = false) String username, Model model, Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);

        // Load friend requests
        List<FriendRequest> receivedRequests = friendRequestService.getPendingReceivedRequests(currentUser);
        model.addAttribute("receivedRequests", receivedRequests);

        // Search for users
        List<Client> users = (username == null || username.trim().isEmpty()) ? List.of() : clientService.findByUsernameContainingIgnoreCase(username);
        if (currentUser != null) {
            users = users.stream().filter(user -> !user.equals(currentUser)).collect(Collectors.toList());
        }
        model.addAttribute("users", users);
        model.addAttribute("searchQuery", username);

        // Load friends list
        List<Client> friends = friendshipService.getFriends(currentUser);
        model.addAttribute("friends", friends);

        return "friendsList";
    }
    /**
     * Accept a friend request.
     */
    @PostMapping("/acceptRequest/{requestId}")
    public String acceptFriendRequest(@PathVariable("requestId") Integer requestId, Authentication authentication, Model model) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);

        try {
            friendRequestService.acceptFriendRequest(requestId, currentUser);
            model.addAttribute("success", "Friend request accepted.");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "redirect:/friends/list";
    }

    /**
     * Reject a friend request.
     */
    @PostMapping("/rejectRequest/{requestId}")
    @ResponseBody
    public String rejectFriendRequest(@PathVariable("requestId") Integer requestId, Authentication authentication, Model model) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);

        try {
            friendRequestService.rejectFriendRequest(requestId, currentUser);
            model.addAttribute("success", "Friend request rejected.");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "redirect:/friends/list";
    }

    /**
     * View the list of friends.
     */
//    @GetMapping("/list")
//    public String viewFriends(Model model, Authentication authentication) {
//        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);
//        List<Client> friends = friendshipService.getFriends(currentUser);
//        model.addAttribute("friends", friends);
//        return "friendsList";
//    }

    /**
     * Notification DTO for AJAX responses.
     */
    @Data
    @AllArgsConstructor
    public static class NotificationDto {
        private Integer requestId;
        private String senderUsername;
    }

    /**
     * Endpoint to fetch notifications via AJAX.
     */
//    @GetMapping("/notifications")
//    public @ResponseBody List<NotificationDto> getNotifications(Authentication authentication) {
//        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);
//        List<FriendRequest> receivedRequests = friendRequestService.getPendingReceivedRequests(currentUser);
//        List<NotificationDto> notifications = receivedRequests.stream()
//                .map(req -> new NotificationDto(req.getId(), req.getSender().getUsername()))
//                .collect(Collectors.toList());
//        return notifications;
//    }

    /**
     * Принимает запрос на дружбу.
     */
    @PostMapping("/acceptRequest")
    @ResponseBody
    public  String acceptFriendRequest(@RequestParam("requestId") Integer requestId, Authentication authentication) {
        Client receiver = clientService.findByUsername(authentication.getName()).orElse(null);
        if (receiver == null) {
            return "User not found.";
        }

        try {
            friendRequestService.acceptFriendRequest(requestId, receiver);
            return "success";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Отклоняет запрос на дружбу.
     */
    @PostMapping("/rejectRequest")
    @ResponseBody
    public String rejectFriendRequest(@RequestParam("requestId") Integer requestId, Authentication authentication) {
        Client receiver = clientService.findByUsername(authentication.getName()).orElse(null);
        if (receiver == null) {
            return "User not found.";
        }

        try {
            friendRequestService.rejectFriendRequest(requestId, receiver);
            return "success";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Endpoint для получения уведомлений через AJAX.
     */
    @GetMapping("/notifications")
    @ResponseBody
    public List<NotificationDto> getNotifications(Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);
        if (currentUser == null) {
            return List.of(); // Возвращаем пустой список, если пользователь не найден
        }
        List<FriendRequest> receivedRequests = friendRequestService.getPendingReceivedRequests(currentUser);
        return receivedRequests.stream()
                .map(req -> new NotificationDto(req.getId(), req.getSender().getUsername()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{friendId}")
    public String viewFriendProfile(@PathVariable("friendId") int friendId,
                                    Model model,
                                    Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + authentication.getName()));

        Client friend = clientService.findOne(friendId);
        if (friend == null) {
            model.addAttribute("error", "Пользователь не найден.");
            return "error"; // Убедитесь, что у вас есть шаблон error.html
        }

        // Проверка, что текущий пользователь является другом
        if (!friendshipService.areFriends(currentUser, friend)) {
            model.addAttribute("error", "Вы не являетесь другом этого пользователя.");
            return "error";
        }

        List<Project> friendsProjects = projectService.findByClient(friend);
        List<ProjectInfo> projectsWithRoles = friendsProjects.stream().map(project -> {
            boolean isOwner = projectTeamService.findByClient(currentUser).equals(currentUser.getId());

            Optional<ProjectTeam> team = projectTeamService.findByProjectAndClient(project, currentUser);
            System.out.println(team);
            boolean isCollaborator = team.map(t -> t.getRole() == ProjectTeam.Role.COLLABORATOR).orElse(false);
            System.out.println("Project: " + project.getName() + ", Is Owner: " + isOwner + ", Is Collaborator: " + isCollaborator);

            return new ProjectInfo(project, isOwner, isCollaborator);
        }).collect(Collectors.toList());

        model.addAttribute("friend", friend);
        model.addAttribute("projects", friendsProjects);
        model.addAttribute("projectsWithRoles", projectsWithRoles);

        return "friendProfile";
    }
    @Data
    @AllArgsConstructor
    public static class ProjectInfo {
        private Project project;
        private boolean isOwner;
        private boolean isCollaborator;
    }
    /**
     * Отправить приглашение на проект друга.
     */
    @PostMapping("/inviteProject/{friendId}")
    public String inviteToProjectAlternative(@PathVariable("friendId") int friendId,
                                             @RequestParam("projectId") int projectId,
                                             Authentication authentication,
                                             Model model) {
        return inviteToProject(friendId, projectId, authentication, model);
    }
    @PostMapping("/{friendId}/invite")
    public String inviteToProject(@PathVariable("friendId") int friendId,
                                  @RequestParam("projectId") int projectId,
                                  Authentication authentication,
                                  Model model) {
        try {
            Client sender = clientService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new Exception("Отправитель не найден."));
            Client receiver = clientService.findOne(friendId);
            if (receiver == null) {
                throw new Exception("Получатель не найден.");
            }
            Project project = projectService.findById(projectId)
                    .orElseThrow(() -> new Exception("Проект не найден."));

            // Проверка, что отправитель является создателем проекта
            if (!projectService.isProjectCreator(project, sender)) {
                throw new Exception("Только создатель проекта может отправлять приглашения.");
            }

            projectInvitationService.sendInvitation(project, sender, receiver);
            model.addAttribute("success", "Приглашение на проект успешно отправлено.");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        // Перенаправление обратно на страницу профиля друга
        return "redirect:/friends/" + friendId;
    }

}
