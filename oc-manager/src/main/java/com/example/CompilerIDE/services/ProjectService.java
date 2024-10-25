package com.example.CompilerIDE.services;

import com.example.CompilerIDE.Dto.FileNodeDto;
import com.example.CompilerIDE.Dto.JsTreeNodeDto;
import com.example.CompilerIDE.client.FileStorageClient;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.util.HashUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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


    @Transactional
    public void saveProjectFilesFromJson(Project project, List<FileNodeDto> files) throws Exception {
        try {
            // 1. Удаление всех объектов в MinIO под префиксом проекта
            String prefix = "projects/" + project.getId() + "/";
            minioService.deleteAllObjectsWithPrefix(prefix);

            // 2. Удаление всех записей ProjectStruct из базы данных для данного проекта
            List<ProjectStruct> existingStructs = projectStructRepository.findByProject(project);
            projectStructRepository.deleteAll(existingStructs);
            logger.info("Удалены все записи ProjectStruct для проекта ID: {}", project.getId());

            // 3. Сохранение новых файлов и папок
            for (FileNodeDto fileDto : files) {
                processFileNode(project, fileDto);
            }

            logger.info("Проект ID: {} успешно обновлён.", project.getId());
        } catch (Exception e) {
            logger.error("Ошибка при сохранении файлов проекта ID: {}", project.getId(), e);
            throw e; // Транзакция будет отменена из-за исключения
        }
    }

    /**
     * Рекурсивная обработка узла файла или папки
     *
     * @param project Объект Project
     * @param fileDto DTO узла файла или папки
     * @throws Exception При ошибках взаимодействия с MinIO
     */
    private void processFileNode(Project project, FileNodeDto fileDto) throws Exception {
        String path = fileDto.getPath();

        // Проверка наличия пути
        if (path == null || path.isEmpty()) {
            logger.warn("Пропущен узел без поля 'path' или с пустым путем");
            return;
        }

        // Определение типа узла: файл или папка
        boolean isFile = fileDto.getContent() != null;
        String type = isFile ? "file" : "folder";

        // Создание или обновление записи в базе данных
        ProjectStruct struct = projectStructRepository.findByProjectAndPath(project, path)
                .orElseGet(() -> {
                    ProjectStruct newStruct = new ProjectStruct();
                    newStruct.setProject(project);
                    newStruct.setPath(path);
                    newStruct.setName(extractNameFromPath(path));
                    newStruct.setType(type);
                    // Вычисляем hash для файла, если это файл
                    if (isFile) {
                        try (InputStream is = new ByteArrayInputStream(fileDto.getContent().getBytes(StandardCharsets.UTF_8))) {
                            String hash = HashUtil.computeSHA256Hash(is);
                            newStruct.setHash(hash);
                        } catch (Exception e) {
                            logger.error("Ошибка при вычислении хеша для файла: {}", path, e);
                            throw new RuntimeException("Ошибка при вычислении хеша", e);
                        }
                    } else {
                        // Для папок используем UUID
                        newStruct.setHash(UUID.randomUUID().toString());
                    }
                    return newStruct;
                });

        if (!struct.getType().equals(type)) {
            struct.setType(type);
            projectStructRepository.save(struct);
            logger.info("Тип узла '{}' обновлён на '{}'", path, type);
        }

        if (isFile) {
            // Обработка файла
            String content = fileDto.getContent();
            String objectKey = "projects/" + project.getId() + "/" + path;

            // Загрузка содержимого файла в MinIO
            minioService.uploadFileContent(objectKey, content.getBytes(StandardCharsets.UTF_8));
            logger.info("Содержимое файла '{}' загружено в MinIO по ключу '{}'", path, objectKey);

            // Hash уже установлен при создании struct
            projectStructRepository.save(struct);
            logger.info("Запись ProjectStruct обновлена для файла: {}", path);
        } else {
            // Обработка папки: создание пустого объекта в MinIO
            String objectKey = "projects/" + project.getId() + "/" + path;
            // Убедимся, что ключ папки оканчивается на "/"
            if (!objectKey.endsWith("/")) {
                objectKey += "/";
            }

            // Создание пустого объекта для папки
            minioService.uploadFileContent(objectKey, new byte[0]);
            logger.info("Папка '{}' создана в MinIO по ключу '{}'", path, objectKey);

            // Сохранение структуры папки
            projectStructRepository.save(struct);
            logger.info("Запись ProjectStruct обновлена для папки: {}", path);
        }

        // Рекурсивная обработка дочерних файлов и папок, если это папка
        if (!isFile && fileDto.getFiles() != null) {
            for (FileNodeDto childFileDto : fileDto.getFiles()) {
                processFileNode(project, childFileDto);
            }
        }
    }




    /**
     * Извлечение имени файла или папки из полного пути
     *
     * @param path Полный путь
     * @return Имя файла или папки
     */
    private String extractNameFromPath(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash != -1 ? path.substring(lastSlash + 1) : path;
    }

    public List<JsTreeNodeDto> buildJsTreeFileStructureFromStructs(Project project, String projectId) {
        List<ProjectStruct> structs = projectStructRepository.findByProject(project);

        JsTreeNodeDto root = new JsTreeNodeDto();
        root.setId("root");
        root.setText("Проект");
        root.setType("folder");
        root.setChildren(new ArrayList<>());
        root.setData(null); // Корневой узел не содержит дополнительных данных

        Map<String, JsTreeNodeDto> nodeMap = new HashMap<>();
        nodeMap.put("", root);

        Map<String, ProjectStruct> structMap = structs.stream()
                .collect(Collectors.toMap(ProjectStruct::getPath, s -> s));

        for (ProjectStruct struct : structs) {
            String path = struct.getPath();
            String[] parts = path.split("/");
            StringBuilder currentPathBuilder = new StringBuilder();

            JsTreeNodeDto current = root;

            for (String part : parts) {
                if (!currentPathBuilder.isEmpty()) {
                    currentPathBuilder.append("/").append(part);
                } else {
                    currentPathBuilder.append(part);
                }
                String currentPath = currentPathBuilder.toString();

                JsTreeNodeDto node = nodeMap.get(currentPath);
                if (node == null) {
                    node = new JsTreeNodeDto();
                    node.setId(currentPath);
                    node.setText(part);
                    node.setChildren(new ArrayList<>());
                    nodeMap.put(currentPath, node);
                    current.getChildren().add(node);
                }

                // Устанавливаем тип узла на основе данных из базы
                if (node.getType() == null) {
                    ProjectStruct nodeStruct = structMap.get(currentPath);
                    if (nodeStruct != null) {
                        node.setType(nodeStruct.getType());
                        if ("file".equals(nodeStruct.getType())) {
                            Map<String, Object> data = new HashMap<>();
                            data.put("content", getFileContent(projectId, currentPath));
                            node.setData(data);
                        } else {
                            node.setData(null);
                        }
                    } else {
                        // Если информации нет, предполагаем, что это папка
                        node.setType("folder");
                        node.setData(null);
                    }
                }

                current = node;
            }
        }

        return Collections.singletonList(root);
    }






    /**
     * Получение содержимого файла из MinIO
     *
     * @param projectId ID проекта
     * @param filePath  Путь файла
     * @return Содержимое файла в виде строки
     */
    private String getFileContent(String projectId, String filePath) {
        try {
            byte[] contentBytes = minioService.getFileContentAsBytes("projects/" + projectId + "/" + filePath);
            return new String(contentBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Ошибка при получении содержимого файла '{}': {}", filePath, e.getMessage());
            return "";
        }
    }

}
