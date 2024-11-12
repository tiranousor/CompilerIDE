package com.example.CompilerIDE.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationDto {
    private Integer requestId;
    private String senderUsername;
}