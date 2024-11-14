package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "project_struct")
public class ProjectStruct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long uid;

    @NotBlank(message = "Название файла или папки не может быть пустым")
    private String name;

    @NotBlank(message = "Путь не может быть пустым")
    private String path;

    @NotBlank(message = "Тип элемента не может быть пустым")
    private String type;

    private String hash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

}
