package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectInvitation;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.ProjectTeam;
import com.example.CompilerIDE.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/invitations")
public class ProjectInvitationController {

    private final ProjectInvitationService projectInvitationService;
    private final ProjectTeamService projectTeamService;
    private final ProjectService projectService;
    private final ClientService clientService;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectAccessLogService projectAccessLogService;
    @Autowired
    public ProjectInvitationController(ProjectInvitationService projectInvitationService, ProjectTeamService projectTeamService,
                                       ProjectService projectService,
                                       ClientService clientService, ProjectAccessLogService projectAccessLogService) {
        this.projectInvitationService = projectInvitationService;
        this.projectTeamService = projectTeamService;
        this.projectService = projectService;
        this.clientService = clientService;
        this.projectAccessLogService = projectAccessLogService;
    }




    @GetMapping("/received")
    public String viewReceivedInvitations(Model model, Authentication authentication) {
        Client receiver = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден."));
        List<ProjectInvitation> invitations = projectInvitationService.getPendingInvitations(receiver);
        model.addAttribute("invitations", invitations);
        return "receivedInvitations";
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

            // Логирование принятия приглашения
            Optional<ProjectInvitation> invitationOpt = projectInvitationService.findById(invitationId);
            invitationOpt.ifPresent(invitation -> {
                Project project = invitation.getProject();
                projectAccessLogService.logAccess(receiver, project, "accept_invitation");
            });

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

            // Логирование отклонения приглашения
            Optional<ProjectInvitation> invitationOpt = projectInvitationService.findById(invitationId);
            invitationOpt.ifPresent(invitation -> {
                Project project = invitation.getProject();
                projectAccessLogService.logAccess(receiver, project, "reject_invitation");
            });

            return "success";
        } catch (Exception e) {
            logger.error("Ошибка при отклонении приглашения ID={}: {}", invitationId, e.getMessage());
            return e.getMessage();
        }
    }

    @GetMapping("/projects/{projectId}/invite")
    public String showInvitePage(@PathVariable("projectId") int projectId, Model model, Authentication authentication) {
        Project project = projectService.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден."));

        Client sender = clientService.findByUsername(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден."));

        boolean isOwner = projectTeamService.findByProjectAndClient(project, sender)
                .map(team -> team.getRole() == ProjectTeam.Role.CREATOR)
                .orElse(false);

        if (!isOwner) {
            model.addAttribute("error", "У вас нет прав приглашать пользователей в этот проект.");
            return "redirect:/userProfile";
        }
        List<ProjectTeam> collaborators = projectTeamService.findByProjectAndRole(project, ProjectTeam.Role.COLLABORATOR);
        model.addAttribute("collaborators", collaborators);

        model.addAttribute("project", project);
        return "inviteUsers"; // Создадим этот шаблон далее
    }


    @GetMapping("/projects/{projectId}/invite/search")
    public String searchUsers(@PathVariable("projectId") int projectId,
                              @RequestParam(value = "query", required = false) String query,
                              Model model, Authentication authentication) {
        Project project = projectService.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Проект не найден."));

        List<Client> users = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            users = clientService.searchByUsername(query.trim());
        }

        model.addAttribute("project", project);
        model.addAttribute("query", query);
        model.addAttribute("users", users);
        return "inviteUsers";
    }


    @PostMapping("/send")
    public String sendInvitation(@RequestParam("projectId") int projectId,
                                 @RequestParam("receiverId") int receiverId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            Client sender = clientService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new Exception("Отправитель не найден."));
            Client receiver = clientService.findOne(receiverId);
            Project project = projectService.findById(projectId)
                    .orElseThrow(() -> new Exception("Проект не найден."));

            projectInvitationService.sendInvitation(project, sender, receiver);
            redirectAttributes.addFlashAttribute("success", "Приглашение успешно отправлено.");

            // Логирование отправки приглашения
            projectAccessLogService.logAccess(sender, project, "send_invitation");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/invitations/projects/" + projectId + "/invite";
    }

    @PostMapping("/projects/{projectId}/removeCollaborator")
    public String removeCollaborator(@PathVariable("projectId") int projectId,
                                     @RequestParam("collaboratorId") int collaboratorId,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            Client sender = clientService.findByUsername(authentication.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("Отправитель не найден."));

            Project project = projectService.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Проект не найден."));

            // Проверяем, является ли отправитель создателем проекта
            boolean isOwner = projectTeamService.findByProjectAndClient(project, sender)
                    .map(team -> team.getRole() == ProjectTeam.Role.CREATOR)
                    .orElse(false);

            if (!isOwner) {
                redirectAttributes.addFlashAttribute("error", "У вас нет прав удалять коллабораторов.");
                return "redirect:/invitations/projects/" + projectId + "/invite";
            }

            Client collaborator = clientService.findOne(collaboratorId);

            projectTeamService.removeCollaborator(project, collaborator);

            redirectAttributes.addFlashAttribute("success", "Коллаборатор успешно удален из проекта.");

        } catch (Exception e) {
            logger.error("Ошибка при удалении коллаборатора: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении коллаборатора: " + e.getMessage());
        }

        return "redirect:/invitations/projects/" + projectId + "/invite";
    }
}
