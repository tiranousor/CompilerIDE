package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectTeam;
import com.example.CompilerIDE.providers.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Integer> {
    List<ProjectTeam> findByProject(Project project);
    Optional<ProjectTeam> findByProjectAndClient(Project project, Client client);
    List<ProjectTeam> findByClientAndRole(Client client, Role role);
    List<ProjectTeam> findByProjectAndRole(Project project, Role role);
}