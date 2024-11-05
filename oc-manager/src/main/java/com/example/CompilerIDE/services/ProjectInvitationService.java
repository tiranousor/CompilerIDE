package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectInvitation;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.ProjectTeam;
import com.example.CompilerIDE.repositories.ProjectInvitationRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProjectInvitationService {

    private final ProjectInvitationRepository projectInvitationRepository;
    private final ProjectTeamService projectTeamService;

    @Autowired
    public ProjectInvitationService(ProjectInvitationRepository projectInvitationRepository,
                                    ProjectTeamService projectTeamService) {
        this.projectInvitationRepository = projectInvitationRepository;
        this.projectTeamService = projectTeamService;
    }

    public void sendInvitation(Project project, Client sender, Client receiver) throws Exception {
        if (sender.equals(receiver)) {
            throw new Exception("Нельзя отправить приглашение самому себе.");
        }

        // Проверка на дублирующееся приглашение
        Optional<ProjectInvitation> existingInvitation = projectInvitationRepository.findByProjectAndReceiverAndStatus(
                project, receiver, ProjectInvitation.Status.PENDING
        );

        if (existingInvitation.isPresent()) {
            throw new Exception("Приглашение уже отправлено.");
        }
        Optional<ProjectTeam> projectTeam = projectTeamService.findByProjectAndClient(project, sender);
        if (projectTeam.isEmpty() || projectTeam.get().getRole() != ProjectTeam.Role.CREATOR) {
            throw new Exception("Только создатель проекта может отправлять приглашения.");
        }
        // Создание нового приглашения
        ProjectInvitation invitation = new ProjectInvitation();
        invitation.setProject(project);
        invitation.setSender(sender);
        invitation.setReceiver(receiver);
        invitation.setStatus(ProjectInvitation.Status.PENDING);
        invitation.setTimestamp(new Timestamp(System.currentTimeMillis()));

        projectInvitationRepository.save(invitation); // Сохранение приглашения в базе данных
    }

    public void acceptInvitation(Integer invitationId, Client receiver) throws Exception {
        ProjectInvitation invitation = projectInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new Exception("Приглашение не найдено."));

        if (!invitation.getReceiver().equals(receiver)) {
            throw new Exception("Вы не можете принять это приглашение.");
        }

        if (invitation.getStatus() != ProjectInvitation.Status.PENDING) {
            throw new Exception("Приглашение уже обработано.");
        }

        // Добавление пользователя в проект
        projectTeamService.addCollaborator(invitation.getProject(), receiver);

        // Обновление статуса приглашения
        invitation.setStatus(ProjectInvitation.Status.ACCEPTED);
        projectInvitationRepository.save(invitation);
    }

    public void rejectInvitation(Integer invitationId, Client receiver) throws Exception {
        ProjectInvitation invitation = projectInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new Exception("Приглашение не найдено."));

        if (!invitation.getReceiver().equals(receiver)) {
            throw new Exception("Вы не можете отклонить это приглашение.");
        }

        if (invitation.getStatus() != ProjectInvitation.Status.PENDING) {
            throw new Exception("Приглашение уже обработано.");
        }

        // Обновление статуса приглашения
        invitation.setStatus(ProjectInvitation.Status.REJECTED);
        projectInvitationRepository.save(invitation);
    }

    public List<ProjectInvitation> getPendingInvitationsForReceiver(Client receiver) {
        return projectInvitationRepository.findByReceiverAndStatus(receiver, ProjectInvitation.Status.PENDING);
    }
    public List<ProjectInvitation> getPendingInvitations(Client receiver) {
        return projectInvitationRepository.findByReceiverAndStatus(receiver, ProjectInvitation.Status.PENDING);
    }
    public List<ProjectInvitation> getSentInvitations(Client sender) {
        return projectInvitationRepository.findBySenderAndStatus(sender, ProjectInvitation.Status.PENDING);
    }

}
