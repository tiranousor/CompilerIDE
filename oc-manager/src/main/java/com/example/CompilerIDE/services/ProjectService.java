package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.repositories.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    @Autowired
    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }
    public List<Project> showAllProjects() {
        return projectRepository.findAll();
    }
    public List<Project> findAll() {
        return projectRepository.findAll();
    }
    public Project findOne(int id) {
        Optional<Project> foundBook = projectRepository.findById(id);
        return foundBook.orElse(null);
    }

    public List<Project> findOne(String name) {
        return projectRepository.findByName(name);
    }

    @Transactional
    public void save(Project project) {
        projectRepository.save(project);
    }
    @Transactional
    public void update(int id, Project updateProject){
        updateProject.setId(id);
        projectRepository.save(updateProject);
    }
    // Method to find a project by ID
    public Optional<Project> findById(int projectId) {
        return projectRepository.findById(projectId);
    }

    // Method to delete a project
    public void delete(Project project) {
        projectRepository.delete(project);
    }
    public List<Project> findByClient(Client client) {
        return projectRepository.findByClient(client);
    }
}
