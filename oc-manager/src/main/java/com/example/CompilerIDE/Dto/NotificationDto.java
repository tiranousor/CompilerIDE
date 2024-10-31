package com.example.CompilerIDE.Dto;

import com.example.CompilerIDE.providers.Project;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
@Data
@AllArgsConstructor
public class NotificationDto {
    private Integer requestId;
    private String senderUsername;

}