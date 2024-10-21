package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "project_structs")
public class ProjectStruct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long uid; // Уникальный идентификатор структуры проекта (первичный ключ).

    @NotBlank(message = "Название файла или папки не может быть пустым")
    private String name; // Название файла или папки.

    @NotBlank(message = "Путь не может быть пустым")
    private String path; // Путь к элементу структуры.

    @NotBlank(message = "Тип элемента не может быть пустым")
    private String type; // Тип элемента (например, файл или папка).

    private String hash; // Хеш-сумма содержимого файла, используемая для проверки изменений.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project; // Связь с проектом.

    // Helper method to determine if the struct is a folder
    public boolean isFolder() {
        return "folder".equalsIgnoreCase(this.type);
    }
}
