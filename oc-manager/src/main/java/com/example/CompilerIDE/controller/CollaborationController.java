//
//package com.example.CompilerIDE.controller;
//
//import com.example.CompilerIDE.providers.Project;
//import com.example.CompilerIDE.providers.Role;
//import com.example.CompilerIDE.services.ProjectService;
//import lombok.Data;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.messaging.handler.annotation.MessageMapping;
//import org.springframework.messaging.handler.annotation.SendTo;
//import org.springframework.stereotype.Controller;
//
//@Data
//class CollaborationMessage {
//    private Long projectId;
//    private String username;
//    private String action; // e.g., "editing", "stopped_editing"
//}
//
//@Data
//class CollaborationStatus {
//    private String username;
//    private String status; // e.g., "editing", "idle"
//}
//
//@Controller
//public class CollaborationController {
//
//    private final ProjectService projectService;
//
//    @Autowired
//    public CollaborationController(ProjectService projectService) {
//        this.projectService = projectService;
//    }
//
//    @MessageMapping("/collaborate")
//    @SendTo("/topic/collaboration")
//    public CollaborationStatus collaborate(CollaborationMessage message) {
//        Project project = projectService.findById(message.getProjectId()).orElse(null);
//        if (project == null) {
//            return null;
//        }
//
//        // Check if the user has permission
//        // Assuming you have access to the current user's information
//        // This might require additional setup with Spring Security and WebSocket authentication
//
//        CollaborationStatus status = new CollaborationStatus();
//        status.setUsername(message.getUsername());
//        status.setStatus(message.getAction());
//        return status;
//    }
//}
