package com.example.CompilerIDE.services;

import com.example.CompilerIDE.client.FileStorageClient;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectStructRepository projectStructRepository;
    private final FileStorageClient fileStorageClient;
    @Autowired
    public ProjectService(ProjectRepository projectRepository, ProjectStructRepository projectStructRepository, FileStorageClient fileStorageClient) {
        this.projectRepository = projectRepository;
        this.projectStructRepository = projectStructRepository;
        this.fileStorageClient = fileStorageClient;
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
    public Optional<Project> findByNameAndClient(String name, Client client){
            return projectRepository.findByNameAndClient(name, client);
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

    public void saveProjectFiles(Project project, List<MultipartFile> files, String path) {
        for (MultipartFile file : files) {
            // Сохраняем информацию о файле в БД
            ProjectStruct projectStruct = new ProjectStruct();
            projectStruct.setProject(project);
            projectStruct.setName(file.getOriginalFilename());
            projectStruct.setPath(path);
            projectStruct.setType("file");
            projectStructRepository.save(projectStruct);

            // Отправляем файл на File Storage Server
            fileStorageClient.uploadFile(project.getId().toString(), file, path);
        }
    }

}
