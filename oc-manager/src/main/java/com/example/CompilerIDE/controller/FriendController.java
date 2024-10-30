package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.FriendRequest;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.FriendRequestService;
import com.example.CompilerIDE.services.FriendshipService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/friends")
public class FriendController {

    private final ClientService clientService;
    private final FriendRequestService friendRequestService;
    private final FriendshipService friendshipService;

    @Autowired
    public FriendController(ClientService clientService, FriendRequestService friendRequestService, FriendshipService friendshipService) {
        this.clientService = clientService;
        this.friendRequestService = friendRequestService;
        this.friendshipService = friendshipService;
    }

    /**
     * Handle GET requests to /friends/search
     * - If 'username' parameter is present, perform the search.
     * - If 'username' parameter is absent, display the search form.
     */
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
    public String sendFriendRequest(@PathVariable("receiverId") Integer receiverId, Authentication authentication, Model model) {
        Client sender = clientService.findByUsername(authentication.getName()).orElse(null);
        Client receiver = clientService.findOne(receiverId);
        if (sender == null || receiver == null) {
            model.addAttribute("error", "User not found.");
            return "redirect:/friends/search";
        }

        try {
            friendRequestService.sendFriendRequest(sender, receiver);
            model.addAttribute("success", "Friend request sent.");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        // Redirect back to the search page with the original query
        return "redirect:/friends/search?username=" + sender.getUsername();
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

        return "redirect:/friends/requests";
    }

    /**
     * Reject a friend request.
     */
    @PostMapping("/rejectRequest/{requestId}")
    public String rejectFriendRequest(@PathVariable("requestId") Integer requestId, Authentication authentication, Model model) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);

        try {
            friendRequestService.rejectFriendRequest(requestId, currentUser);
            model.addAttribute("success", "Friend request rejected.");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "redirect:/friends/requests";
    }

    /**
     * View the list of friends.
     */
    @GetMapping("/list")
    public String viewFriends(Model model, Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);
        List<Client> friends = friendshipService.getFriends(currentUser);
        model.addAttribute("friends", friends);
        return "friendsList";
    }

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
    public @ResponseBody String acceptFriendRequest(@RequestParam("requestId") Integer requestId, Authentication authentication) {
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
    public @ResponseBody String rejectFriendRequest(@RequestParam("requestId") Integer requestId, Authentication authentication) {
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
    public @ResponseBody List<NotificationDto> getNotifications(Authentication authentication) {
        Client currentUser = clientService.findByUsername(authentication.getName()).orElse(null);
        if (currentUser == null) {
            return List.of(); // Возвращаем пустой список, если пользователь не найден
        }
        List<FriendRequest> receivedRequests = friendRequestService.getPendingReceivedRequests(currentUser);
        return receivedRequests.stream()
                .map(req -> new NotificationDto(req.getId(), req.getSender().getUsername()))
                .collect(Collectors.toList());
    }


}
