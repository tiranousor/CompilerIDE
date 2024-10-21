package com.example.CompilerIDE.services;

import com.example.CompilerIDE.client.FileStorageClient;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectStructRepository projectStructRepository;
    private final FileStorageClient fileStorageClient;
    private final MinioService minioService;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    @Autowired
    public ProjectService(ProjectRepository projectRepository, ProjectStructRepository projectStructRepository,
                          FileStorageClient fileStorageClient, MinioService minioService) {
        this.projectRepository = projectRepository;
        this.minioService = minioService;
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

    public List<String> getFilePaths(Project project) {
        return projectStructRepository.findByProjectAndType(project, "file")
                .stream()
                .map(ProjectStruct::getPath)
                .collect(Collectors.toList());
    }

    public List<String> getFileNames(Project project) {
        return projectStructRepository.findByProjectAndType(project, "file")
                .stream()
                .map(ProjectStruct::getName)
                .collect(Collectors.toList());
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

//    public void saveProjectFiles(Project project, List<MultipartFile> files, String path) {
//        for (MultipartFile file : files) {
//            // Сохраняем информацию о файле в БД
//            ProjectStruct projectStruct = new ProjectStruct();
//            projectStruct.setProject(project);
//            projectStruct.setName(file.getOriginalFilename());
//            projectStruct.setPath(path);
//            projectStruct.setType("file");
//            projectStructRepository.save(projectStruct);
//
//            // Отправляем файл на File Storage Server
//            fileStorageClient.uploadFile(project.getId().toString(), file, path);
//        }
//    }


    // Метод для сохранения структуры проекта
    public void saveProjectStruct(ProjectStruct projectStruct) {
        projectStructRepository.save(projectStruct);
    }

    @Transactional
    public void saveProjectFiles(Project project, List<MultipartFile> files, String basePath) throws Exception {
        // Получаем текущие записи ProjectStruct для проекта
        List<ProjectStruct> existingProjectStructs = projectStructRepository.findByProjectAndType(project, "file");
        Map<String, ProjectStruct> existingFilesMap = existingProjectStructs.stream()
                .collect(Collectors.toMap(ProjectStruct::getPath, ps -> ps));

        // Создаём множество путей из новых файлов
        Set<String> newFilePaths = files.stream()
                .map(file -> basePath.isEmpty() ? file.getOriginalFilename() : basePath + "/" + file.getOriginalFilename())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Определяем файлы для удаления: существующие в БД, но отсутствующие в новом списке
        Set<String> filesToDelete = new HashSet<>(existingFilesMap.keySet());
        filesToDelete.removeAll(newFilePaths);

        // Удаляем файлы, которых нет в новом списке
        for (String pathToDelete : filesToDelete) {
            ProjectStruct structToDelete = existingFilesMap.get(pathToDelete);
            if (structToDelete != null) {
                String objectKey = "projects/" + project.getId() + "/" + structToDelete.getPath();
                minioService.deleteFile(objectKey);
                projectStructRepository.delete(structToDelete);
                logger.info("Deleted ProjectStruct and MinIO file: {}", objectKey);
            }
        }

        // Обрабатываем добавление и обновление файлов
        for (MultipartFile file : files) {
            String fullPath = file.getOriginalFilename();
            if (fullPath == null) {
                logger.warn("Original filename is null for a file in project {}", project.getId());
                continue; // Пропускаем текущую итерацию цикла
            }

            String relativePath = basePath.isEmpty() ? fullPath : basePath + "/" + fullPath;
            String objectKey = "projects/" + project.getId() + "/" + relativePath;

            // Разбиваем путь на части (папки и файл) и создаём записи для папок, если необходимо
            Path path = Paths.get(fullPath);
            Path parent = path.getParent();
            if (parent != null) {
                Path cumulativePath = Paths.get("");
                for (Path part : parent) {
                    cumulativePath = cumulativePath.resolve(part);
                    String folderPath = cumulativePath.toString().replace("\\", "/"); // Для Windows-путей

                    // Проверяем, существует ли уже папка
                    Optional<ProjectStruct> existingFolder = projectStructRepository.findByProjectAndPathAndType(
                            project,
                            folderPath,
                            "folder"
                    );
                    if (existingFolder.isEmpty()) {
                        // Если папки нет, создаём её
                        ProjectStruct folderStruct = new ProjectStruct();
                        folderStruct.setProject(project);
                        folderStruct.setName(part.getFileName().toString()); // Только имя папки
                        folderStruct.setPath(folderPath); // Полный путь к папке
                        folderStruct.setType("folder");
                        projectStructRepository.save(folderStruct);
                        logger.info("Created folder: {}", folderPath);
                    }
                }
            }

            // Проверяем, существует ли уже файл
            ProjectStruct existingFileStruct = existingFilesMap.get(relativePath);
            if (existingFileStruct != null) {
                // Обновляем существующий файл
                // Загрузка нового файла в MinIO (перезапись)
                minioService.uploadFile(
                        objectKey,
                        file.getInputStream(),
                        file.getSize(),
                        file.getContentType()
                );
                logger.info("Updated MinIO file: {}", objectKey);

                // Вычисление хеша файла
                String hash;
                try (InputStream is = file.getInputStream()) {
                    hash = HashUtil.computeSHA256Hash(is);
                }

                // Обновляем запись в базе данных
                existingFileStruct.setHash(hash);
                projectStructRepository.save(existingFileStruct);
                logger.info("Updated ProjectStruct for file: {}", relativePath);
            } else {
                // Добавляем новый файл
                // Загрузка файла в MinIO
                minioService.uploadFile(
                        objectKey,
                        file.getInputStream(),
                        file.getSize(),
                        file.getContentType()
                );
                logger.info("Uploaded new file to MinIO: {}", objectKey);

                // Вычисление хеша файла
                String hash;
                try (InputStream is = file.getInputStream()) {
                    hash = HashUtil.computeSHA256Hash(is);
                }

                // Создаём новую запись в базе данных
                ProjectStruct newFileStruct = new ProjectStruct();
                newFileStruct.setProject(project);
                newFileStruct.setName(path.getFileName().toString()); // Только имя файла
                newFileStruct.setPath(relativePath); // Полный путь к файлу
                newFileStruct.setType("file");
                newFileStruct.setHash(hash);

                projectStructRepository.save(newFileStruct);
                logger.info("Saved new ProjectStruct for file: {}", relativePath);
            }
        }
    }


}
