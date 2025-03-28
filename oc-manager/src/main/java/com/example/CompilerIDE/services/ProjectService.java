package com.example.CompilerIDE.services;

import com.example.CompilerIDE.dto.FileNodeDto;
import com.example.CompilerIDE.dto.JsTreeNodeDto;
import com.example.CompilerIDE.providers.*;
import com.example.CompilerIDE.repositories.ProjectRepository;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import com.example.CompilerIDE.util.HashUtil;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.springframework.util.FileSystemUtils;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectStructRepository projectStructRepository;
    private final MinioService minioService;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    @Value("${git.clone.directory}")
    private String gitCloneDirectory;
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

    public void importFromGit(Project project) throws Exception {
        String repoUrl = project.getRefGit();
        String projectDirPath = gitCloneDirectory + File.separator + "project-" + project.getId();
        File projectDir = new File(projectDirPath);

        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(projectDir)
                .setCloneAllBranches(true)
                .setTimeout(60);

        try (Git git = cloneCommand.call()) {
            processClonedRepository(project, projectDir);
        } catch (Exception e) {
            FileSystemUtils.deleteRecursively(projectDir);
            throw new Exception("Не удалось клонировать репозиторий: " + e.getMessage(), e);
        }

        FileSystemUtils.deleteRecursively(projectDir);
    }


    private void processClonedRepository(Project project, File projectDir) throws Exception {
        File[] files = projectDir.listFiles();

        if (files == null || files.length == 0) {
            logger.warn("Клонированный репозиторий пуст.");
            return;
        }

        logger.info("Обнаружены файлы/папки после клонирования: {}", Arrays.toString(files));
        if (files.length == 1 && files[0].isDirectory()) {
            File innerFolder = files[0];
            logger.info("Перенос содержимого папки '{}' в корень '{}'", innerFolder.getName(), projectDir.getPath());

            File[] innerFiles = innerFolder.listFiles();
            if (innerFiles != null) {
                for (File innerFile : innerFiles) {
                    File targetFile = new File(projectDir, innerFile.getName());
                    if (innerFile.renameTo(targetFile)) {
                        logger.info("Файл/папка '{}' успешно перемещён в '{}'", innerFile.getName(), projectDir.getPath());
                    } else {
                        throw new IOException("Не удалось переместить файл " + innerFile.getName());
                    }
                }
            }
            if (innerFolder.delete()) {
                logger.info("Папка '{}' успешно удалена.", innerFolder.getName());
            } else {
                logger.warn("Не удалось удалить папку '{}'", innerFolder.getName());
            }
        }
        for (File file : projectDir.listFiles()) {
            traverseAndProcess(project, file, "");
        }
    }


    private void traverseAndProcess(Project project, File currentFile, String currentPath) throws Exception {
        if (currentFile.isDirectory()) {
            String dirPath = currentPath + currentFile.getName() + "/";
            ProjectStruct struct = new ProjectStruct();
            struct.setName(currentFile.getName());
            struct.setPath(dirPath);
            struct.setType("folder");
            struct.setProject(project);
            projectStructRepository.save(struct);

            String objectKey = "projects/" + project.getId() + "/" + struct.getPath();
            minioService.uploadFileContent(objectKey, new byte[0]);
            for (File file : Objects.requireNonNull(currentFile.listFiles())) {
                traverseAndProcess(project, file, dirPath);
            }
        } else if (currentFile.isFile()) {
            String filePath = currentPath + currentFile.getName();
            ProjectStruct struct = new ProjectStruct();
            struct.setName(currentFile.getName());
            struct.setPath(filePath);
            struct.setType("file");
            struct.setProject(project);

            try {
                String hash = HashUtil.computeSHA256Hash(currentFile);
                struct.setHash(hash);
            } catch (Exception e) {
                logger.error("Ошибка при вычислении хеша для файла {}: {}", filePath, e.getMessage());
                throw new Exception("Ошибка при вычислении хеша для файла " + filePath, e);
            }

            projectStructRepository.save(struct);
            String objectKey = "projects/" + project.getId() + "/" + struct.getPath();
            byte[] contentBytes = FileUtils.readFileToByteArray(currentFile);
            minioService.uploadFileContent(objectKey, contentBytes);
        }
    }
    public List<JsTreeNodeDto> buildJsTreeFileStructureFromStructs(Project project, String projectId) {
        List<ProjectStruct> structs = projectStructRepository.findByProject(project);
        logger.info("Building jsTree structure for project: {}", project.getName());

        JsTreeNodeDto root = new JsTreeNodeDto();
        root.setId("root");
        root.setText(project.getName());
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
                    logger.debug("Added node: {}", currentPath);
                }

                if (node.getType() == null) {
                    ProjectStruct nodeStruct = structMap.get(currentPath);
                    if (nodeStruct != null) {
                        node.setType(nodeStruct.getType());
                        if ("file".equals(nodeStruct.getType())) {
                            Map<String, Object> data = new HashMap<>();
                            data.put("content", getFileContent(projectId, currentPath));
                            node.setData(data);
                            logger.debug("Set data for file node: {}", currentPath);
                        } else {
                            node.setData(null);
                            logger.debug("Set data for folder node: {}", currentPath);
                        }
                    } else {
                        node.setType("folder");
                        node.setData(null);
                        logger.debug("Set data for folder node: {}", currentPath);
                    }
                }

                current = node;
            }
        }

        logger.info("jsTree structure built successfully for project: {}", project.getName());
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
