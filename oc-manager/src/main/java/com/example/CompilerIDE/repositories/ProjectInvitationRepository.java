
package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectInvitation;
import com.example.CompilerIDE.providers.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {
    List<ProjectInvitation> findByInvitedUserAndStatus(Client invitedUser, InvitationStatus status);
    Optional<ProjectInvitation> findByIdAndInvitedUser(Long id, Client invitedUser);
    List<ProjectInvitation> findByProjectAndStatus(Project project, InvitationStatus status);
    Optional<ProjectInvitation> findByProjectAndInvitedUserAndStatus(Project project, Client invitedUser, InvitationStatus status);
    List<ProjectInvitation> findByReceiverAndStatus(Client receiver, InvitationStatus status);

}