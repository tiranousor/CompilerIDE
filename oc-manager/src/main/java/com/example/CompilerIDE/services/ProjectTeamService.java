package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectTeam;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.repositories.ProjectTeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectTeamService {

    private final ProjectTeamRepository projectTeamRepository;

    @Autowired
    public ProjectTeamService(ProjectTeamRepository projectTeamRepository) {
        this.projectTeamRepository = projectTeamRepository;
    }

    public Optional<ProjectTeam> findByProjectAndClient(Project project, Client client) {
        Optional<ProjectTeam> team = projectTeamRepository.findByProjectAndClient(project, client);
        System.out.println("Checking role for client: " + client.getUsername() + " on project: " + project.getName());
        System.out.println("Role found: " + (team.isPresent() ? team.get().getRole() : "None"));
        return team;
    }

    public List<ProjectTeam> findByProject(Project project) {
        return projectTeamRepository.findByProject(project);
    }

    public List<ProjectTeam> findByClient(Client client) {
        return projectTeamRepository.findByClient(client);
    }

    public ProjectTeam addCollaborator(Project project, Client client) {
        ProjectTeam projectTeam = new ProjectTeam();
        projectTeam.setProject(project);
        projectTeam.setClient(client);
        projectTeam.setRole(ProjectTeam.Role.COLLABORATOR);
        return projectTeamRepository.save(projectTeam);
    }
    public ProjectTeam addCreator(Project project, Client client) {
        ProjectTeam projectTeam = new ProjectTeam();
        projectTeam.setProject(project);
        projectTeam.setClient(client);
        projectTeam.setRole(ProjectTeam.Role.CREATOR);
        return projectTeamRepository.save(projectTeam);
    }
    public void removeCollaborator(Project project, Client collaborator) {
        Optional<ProjectTeam> projectTeamOpt = projectTeamRepository.findByProjectAndClient(project, collaborator);
        projectTeamOpt.ifPresent(projectTeamRepository::delete);
    }


    public List<ProjectTeam> findByProjectAndRole(Project project, ProjectTeam.Role role) {
        return projectTeamRepository.findByProjectAndRole(project, role);
    }
}
