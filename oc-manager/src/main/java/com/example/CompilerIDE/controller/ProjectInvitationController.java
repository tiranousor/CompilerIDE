package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectInvitation;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.services.ProjectInvitationService;
import com.example.CompilerIDE.services.ProjectService;
import com.example.CompilerIDE.services.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/invitations")
public class ProjectInvitationController {

    private final ProjectInvitationService projectInvitationService;
    private final ProjectService projectService;
    private final ClientService clientService;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    @Autowired
    public ProjectInvitationController(ProjectInvitationService projectInvitationService,
                                       ProjectService projectService,
                                       ClientService clientService) {
        this.projectInvitationService = projectInvitationService;
        this.projectService = projectService;
        this.clientService = clientService;
    }


    @PostMapping("/send")
    public String sendInvitation(@RequestParam("projectId") int projectId,
                                 @RequestParam("receiverId") int receiverId,
                                 Authentication authentication,
                                 Model model) {
        try {
            Client sender = clientService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new Exception("Отправитель не найден."));
            Client receiver = clientService.findOne(receiverId);
            Project project = projectService.findById(projectId)
                    .orElseThrow(() -> new Exception("Проект не найден."));

            projectInvitationService.sendInvitation(project, sender, receiver);
            model.addAttribute("success", "Приглашение успешно отправлено.");

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/userProfile";
    }


    @GetMapping("/received")
    public String viewReceivedInvitations(Model model, Authentication authentication) {
        Client receiver = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден."));
        List<ProjectInvitation> invitations = projectInvitationService.getPendingInvitations(receiver);
        model.addAttribute("invitations", invitations);
        return "receivedInvitations"; // Создайте соответствующий шаблон Thymeleaf
    }

    @PostMapping("/accept/{id}")
    @ResponseBody
    public String acceptInvitation(@PathVariable("id") Integer invitationId, Authentication authentication) {
        Client receiver = clientService.findByUsername(authentication.getName()).orElse(null);
        if (receiver == null) {
            return "User not found.";
        }

        try {
            projectInvitationService.acceptInvitation(invitationId, receiver);
            logger.info("Приглашение ID={} принято пользователем {}", invitationId, receiver.getUsername());
            return "success";
        } catch (Exception e) {
            logger.error("Ошибка при принятии приглашения ID={}: {}", invitationId, e.getMessage());
            return e.getMessage();
        }
    }

    @PostMapping("/reject/{id}")
    @ResponseBody
    public String rejectInvitation(@PathVariable("id") Integer invitationId, Authentication authentication) {
        Client receiver = clientService.findByUsername(authentication.getName()).orElse(null);
        if (receiver == null) {
            return "User not found.";
        }

        try {
            projectInvitationService.rejectInvitation(invitationId, receiver);
            logger.info("Приглашение ID={} отклонено пользователем {}", invitationId, receiver.getUsername());
            return "success";
        } catch (Exception e) {
            logger.error("Ошибка при отклонении приглашения ID={}: {}", invitationId, e.getMessage());
            return e.getMessage();
        }
    }

}
