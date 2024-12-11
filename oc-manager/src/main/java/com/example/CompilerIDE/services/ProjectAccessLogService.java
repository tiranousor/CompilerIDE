package com.example.CompilerIDE.services;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectAccessLog;
import com.example.CompilerIDE.repositories.ProjectAccessLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class ProjectAccessLogService {

    private final ProjectAccessLogRepository projectAccessLogRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public ProjectAccessLogService(ProjectAccessLogRepository projectAccessLogRepository, MeterRegistry meterRegistry) {
        this.projectAccessLogRepository = projectAccessLogRepository;
        this.meterRegistry = meterRegistry;
    }

    public void logAccess(Client client, Project project, String actionType) {
        ProjectAccessLog log = new ProjectAccessLog();
        log.setClient(client);
        log.setProject(project);
        log.setAccessTime(new Timestamp(System.currentTimeMillis()));
        log.setActionType(actionType);
        projectAccessLogRepository.save(log);

        Counter.builder("project_access_logs_total")
                .tag("project_id", project.getId().toString())  // используем уникальный ID проекта
                .tag("action", actionType)
                .register(meterRegistry)
                .increment();
    }

    // Метод для получения логов доступа по проекту, отсортированных по убыванию
    public List<ProjectAccessLog> getAccessLogsForProject(Project project) {
        return projectAccessLogRepository.findByProjectOrderByAccessTimeDesc(project);
    }

    public void save(ProjectAccessLog project) {
        projectAccessLogRepository.save(project);
    }
}
