package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.ProjectInvitation;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Integer> {
    List<ProjectInvitation> findByReceiverAndStatus(Client receiver, ProjectInvitation.Status status);
    List<ProjectInvitation> findBySenderAndStatus(Client sender, ProjectInvitation.Status status);
    Optional<ProjectInvitation> findByProjectAndSenderAndReceiver(Project project, Client sender, Client receiver);
    Optional<ProjectInvitation> findByProjectAndReceiverAndStatus(Project project, Client receiver, ProjectInvitation.Status status);

}

