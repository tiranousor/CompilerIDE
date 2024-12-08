package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectTeam;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.ProjectTeam.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

@Repository
public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Integer> {
    Optional<ProjectTeam> findByProjectAndClient(Project project, Client client);
    List<ProjectTeam> findByProject(Project project);
    List<ProjectTeam> findByClient(Client client);
    List<ProjectTeam> findByProjectAndRole(Project project, Role role);
    List<ProjectTeam> findByClientAndRole(Client client, Role role);
    List<ProjectTeam> findByProject_ClientAndClientAndRole(Client viewedUser, Client currentUser, Role role);


}
