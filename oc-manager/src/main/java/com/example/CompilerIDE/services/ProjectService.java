package com.example.CompilerIDE.services;

import com.example.CompilerIDE.Dto.FileNodeDto;
import com.example.CompilerIDE.Dto.JsTreeNodeDto;
import com.example.CompilerIDE.providers.*;
import com.example.CompilerIDE.repositories.ProjectInvitationRepository;
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
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import com.example.CompilerIDE.repositories.ProjectTeamRepository;
import com.example.CompilerIDE.providers.Notification;
import com.example.CompilerIDE.providers.NotificationType;
import com.example.CompilerIDE.repositories.NotificationRepository;
@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectStructRepository projectStructRepository;
    private final MinioService minioService;
    private final ProjectTeamRepository projectTeamRepository;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectInvitationRepository projectInvitationRepository;
    private final NotificationRepository notificationRepository;
    @Autowired
    public ProjectService(ProjectRepository projectRepository, ProjectStructRepository projectStructRepository,
                          MinioService minioService, ProjectTeamRepository projectTeamRepository, ProjectInvitationRepository projectInvitationRepository, NotificationRepository notificationRepository) {
        this.projectRepository = projectRepository;
        this.minioService = minioService;
        this.projectStructRepository = projectStructRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.projectInvitationRepository = projectInvitationRepository;
        this.notificationRepository = notificationRepository;
    }

    public Optional<Project> findByNameAndClient(String name, Client client){
        return projectRepository.findByNameAndClient(name, client);
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
    @Transactional
    public void addMemberToProject(Project project, Client client, Role role) {
        Optional<ProjectTeam> existing = projectTeamRepository.findByProjectAndClient(project, client);
        if (existing.isPresent()) {
            throw new RuntimeException("User is already a member of the project.");
        }
        ProjectTeam projectTeam = new ProjectTeam();
        projectTeam.setProject(project);
        projectTeam.setClient(client);
        projectTeam.setRole(role);
        projectTeamRepository.save(projectTeam);
    }
    @Transactional
    public void removeMemberFromProject(Project project, Client client) {
        Optional<ProjectTeam> existing = projectTeamRepository.findByProjectAndClient(project, client);
        existing.ifPresent(projectTeamRepository::delete);
    }
    public List<ProjectTeam> getProjectMembers(Project project) {
        return projectTeamRepository.findByProject(project);
    }
    public List<ProjectTeam> getProjectMembersByRole(Project project, Role role) {
        return projectTeamRepository.findByProjectAndRole(project, role);
    }
    public boolean isMember(Project project, Client client) {
        return projectTeamRepository.findByProjectAndClient(project, client).isPresent();
    }
    public Optional<Role> getUserRoleInProject(Project project, Client client) {
        return projectTeamRepository.findByProjectAndClient(project, client)
                .map(ProjectTeam::getRole);
    }
    @Transactional
    public void sendInvitation(Project project, Client invitedUser, Client invitedBy) {
        // ... existing code ...

        // Create and save the invitation
        ProjectInvitation invitation = new ProjectInvitation();
        invitation.setProject(project);
        invitation.setInvitedUser(invitedUser);
        invitation.setInvitedBy(invitedBy);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitationTime(new Timestamp(System.currentTimeMillis()));
        projectInvitationRepository.save(invitation);

        // Create a notification for the invitee
        Notification notification = new Notification();
        notification.setRecipient(invitedUser);
        notification.setType(NotificationType.PROJECT_INVITATION);
        notification.setMessage(invitedBy.getUsername() + " has invited you to join the project \"" + project.getName() + "\".");
        notification.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    /**
     * Accepts an invitation and creates a notification for the inviter.
     */
    @Transactional
    public void acceptInvitation(Long invitationId, Client invitedUser) {
        ProjectInvitation invitation = projectInvitationRepository.findByIdAndInvitedUser(invitationId, invitedUser)
                .orElseThrow(() -> new RuntimeException("Invitation not found."));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new RuntimeException("Invitation is not pending.");
        }

        // Add the user to the project as an EDITOR
        addMemberToProject(invitation.getProject(), invitedUser, Role.EDITOR);

        // Update the invitation status
        invitation.setStatus(InvitationStatus.ACCEPTED);
        projectInvitationRepository.save(invitation);

        // Notify the inviter about the acceptance
        Notification notification = new Notification();
        notification.setRecipient(invitation.getInvitedBy());
        notification.setType(NotificationType.PROJECT_INVITATION);
        notification.setMessage(invitedUser.getUsername() + " has accepted your invitation to join the project \"" + invitation.getProject().getName() + "\".");
        notification.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    /**
     * Rejects an invitation and notifies the inviter.
     */
    @Transactional
    public void rejectInvitation(Long invitationId, Client invitedUser) {
        ProjectInvitation invitation = projectInvitationRepository.findByIdAndInvitedUser(invitationId, invitedUser)
                .orElseThrow(() -> new RuntimeException("Invitation not found."));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new RuntimeException("Invitation is not pending.");
        }

        // Update the invitation status
        invitation.setStatus(InvitationStatus.REJECTED);
        projectInvitationRepository.save(invitation);

        // Notify the inviter about the rejection
        Notification notification = new Notification();
        notification.setRecipient(invitation.getInvitedBy());
        notification.setType(NotificationType.PROJECT_INVITATION);
        notification.setMessage(invitedUser.getUsername() + " has rejected your invitation to join the project \"" + invitation.getProject().getName() + "\".");
        notification.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        notification.setRead(false);
        notificationRepository.save(notification);
    }
    public List<ProjectInvitation> getPendingInvitations(Client receiver) {
        return projectInvitationRepository.findByReceiverAndStatus(receiver, InvitationStatus.PENDING);
    }

    public List<Notification> getNotifications(Client receiver) {
        // Здесь реализуйте метод для получения уведомлений по логике вашего проекта
        return List.of(); // Предположим, возвращается пустой список как пример
    }
}
