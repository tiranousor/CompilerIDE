package com.example.CompilerIDE.services;

import com.example.CompilerIDE.Dto.FileNodeDto;
import com.example.CompilerIDE.Dto.JsTreeNodeDto;
import com.example.CompilerIDE.providers.*;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectStructRepository projectStructRepository;
    private final MinioService minioService;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectTeamService projectTeamService;
    @Autowired
    public ProjectService(ProjectRepository projectRepository, ProjectStructRepository projectStructRepository,
                          MinioService minioService, ProjectTeamService projectTeamService) {
        this.projectRepository = projectRepository;
        this.minioService = minioService;
        this.projectStructRepository = projectStructRepository;
        this.projectTeamService = projectTeamService;
    }
    public boolean isProjectCreator(Project project, Client client) {
        return project.getClient().equals(client);
    }
    public Optional<Project> findByNameAndClient(String name, Client client){
        return projectRepository.findByNameAndClient(name, client);
    }
    public ProjectTeamService getProjectTeamService() {
        return this.projectTeamService;
    }

    @Transactional
    public void save(Project project) {
        projectRepository.save(project);
    }

    public Optional<Project> findById(int projectId) {
        return projectRepository.findById(projectId);
    }

    public void delete(Project project) {
        projectRepository.delete(project);
    }

    public List<Project> findByClient(Client client) {
        return projectRepository.findByClient(client);
    }

    @Transactional
    public void saveProjectFilesFromJson(Project project, List<FileNodeDto> files) throws Exception {
        try {
            String prefix = "projects/" + project.getId() + "/";
            minioService.deleteAllObjectsWithPrefix(prefix);

            List<ProjectStruct> existingStructs = projectStructRepository.findByProject(project);
            projectStructRepository.deleteAll(existingStructs);
            logger.info("Удалены все записи ProjectStruct для проекта ID: {}", project.getId());

            for (FileNodeDto fileDto : files) {
                processFileNode(project, fileDto);
            }

            logger.info("Проект ID: {} успешно обновлён.", project.getId());
        } catch (Exception e) {
            logger.error("Ошибка при сохранении файлов проекта ID: {}", project.getId(), e);
            throw e;
        }
    }

    private void processFileNode(Project project, FileNodeDto fileDto) throws Exception {
        String path = fileDto.getPath();

        if (path == null || path.isEmpty()) {
            logger.warn("Пропущен узел без поля 'path' или с пустым путем");
            return;
        }

        boolean isFile = fileDto.getContent() != null;
        String type = isFile ? "file" : "folder";

        ProjectStruct struct = projectStructRepository.findByProjectAndPath(project, path)
                .orElseGet(() -> {
                    ProjectStruct newStruct = new ProjectStruct();
                    newStruct.setProject(project);
                    newStruct.setPath(path);
                    newStruct.setName(extractNameFromPath(path));
                    newStruct.setType(type);
                    if (isFile) {
                        try (InputStream is = new ByteArrayInputStream(fileDto.getContent().getBytes(StandardCharsets.UTF_8))) {
                            String hash = HashUtil.computeSHA256Hash(is);
                            newStruct.setHash(hash);
                        } catch (Exception e) {
                            logger.error("Ошибка при вычислении хеша для файла: {}", path, e);
                            throw new RuntimeException("Ошибка при вычислении хеша", e);
                        }
                    } else {
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
            String content = fileDto.getContent();
            String objectKey = "projects/" + project.getId() + "/" + path;

            minioService.uploadFileContent(objectKey, content.getBytes(StandardCharsets.UTF_8));
            logger.info("Содержимое файла '{}' загружено в MinIO по ключу '{}'", path, objectKey);

            projectStructRepository.save(struct);
            logger.info("Запись ProjectStruct обновлена для файла: {}", path);
        } else {
            String objectKey = "projects/" + project.getId() + "/" + path;
            if (!objectKey.endsWith("/")) {
                objectKey += "/";
            }

            minioService.uploadFileContent(objectKey, new byte[0]);
            logger.info("Папка '{}' создана в MinIO по ключу '{}'", path, objectKey);

            projectStructRepository.save(struct);
            logger.info("Запись ProjectStruct обновлена для папки: {}", path);
        }

        if (!isFile && fileDto.getFiles() != null) {
            for (FileNodeDto childFileDto : fileDto.getFiles()) {
                processFileNode(project, childFileDto);
            }
        }
    }

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
        root.setData(null);

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
                        node.setType("folder");
                        node.setData(null);
                    }
                }

                current = node;
            }
        }

        return Collections.singletonList(root);
    }

    private String getFileContent(String projectId, String filePath) {
        try {
            byte[] contentBytes = minioService.getFileContentAsBytes("projects/" + projectId + "/" + filePath);
            return new String(contentBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Ошибка при получении содержимого файла '{}': {}", filePath, e.getMessage());
            return "";
        }
    }

    public boolean canEditProject(Project project, Client client) {
        Optional<ProjectTeam> team = projectTeamService.findByProjectAndClient(project, client);
        return team.isPresent() && (team.get().getRole() == ProjectTeam.Role.CREATOR || team.get().getRole() == ProjectTeam.Role.COLLABORATOR);
    }

    public List<Project> findAccessibleProjects(Client viewedUser, Client currentUser) {
        List<Project> publicProjects = projectRepository.findByClientAndAccessLevel(viewedUser, AccessLevel.PUBLIC);
        List<Project> collaboratorProjects = projectTeamService.findProjectsByClient(viewedUser, currentUser, ProjectTeam.Role.COLLABORATOR);
        return Stream.concat(publicProjects.stream(), collaboratorProjects.stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
