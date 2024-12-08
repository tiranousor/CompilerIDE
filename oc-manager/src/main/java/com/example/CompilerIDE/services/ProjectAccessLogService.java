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

@Service
public class ProjectAccessLogService {

    private final ProjectAccessLogRepository projectAccessLogRepository;
    private final Counter projectViewCounter;
    private final Counter invitationSentCounter;
    private final Counter invitationAcceptedCounter;
    private final Counter invitationRejectedCounter;

    @Autowired
    public ProjectAccessLogService(ProjectAccessLogRepository projectAccessLogRepository, MeterRegistry meterRegistry) {
        this.projectAccessLogRepository = projectAccessLogRepository;
        this.projectViewCounter = meterRegistry.counter("project_access_logs_total", "action", "view");
        this.invitationSentCounter = meterRegistry.counter("project_access_logs_total", "action", "send_invitation");
        this.invitationAcceptedCounter = meterRegistry.counter("project_access_logs_total", "action", "accept_invitation");
        this.invitationRejectedCounter = meterRegistry.counter("project_access_logs_total", "action", "reject_invitation");
    }

    public void logAccess(Client client, Project project, String actionType) {
        ProjectAccessLog log = new ProjectAccessLog();
        log.setClient(client);
        log.setProject(project);
        log.setAccessTime(new Timestamp(System.currentTimeMillis()));
        log.setActionType(actionType);
        projectAccessLogRepository.save(log);

        // Увеличение соответствующего счетчика
        switch (actionType) {
            case "view":
                projectViewCounter.increment();
                break;
            case "send_invitation":
                invitationSentCounter.increment();
                break;
            case "accept_invitation":
                invitationAcceptedCounter.increment();
                break;
            case "reject_invitation":
                invitationRejectedCounter.increment();
                break;
            default:
                break;
        }
    }
}
