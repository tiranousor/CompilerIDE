package com.example.CompilerIDE.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
public class ProjectAccessLogDTO {
    private String username;
    private String actionType;
    private Timestamp accessTime;
    private boolean owner;
}
