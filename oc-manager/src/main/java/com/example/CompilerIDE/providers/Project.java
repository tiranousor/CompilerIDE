package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.URL;

import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "projects", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"name", "user_id"})
})
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = true, unique = true, name="uuid")
    private String uuid;

    @NotBlank(message = "Название проекта не может быть пустым")
    @Size(max = 100, message = "Название проекта должно быть не длиннее 100 символов")
    private String name;

    @NotBlank(message = "Язык программирования не может быть пустым")
    private String language;

    private String readMe = "Мой первый проект";

    @URL(message = "Неверный формат URL")
    private String refGit;

    @Column(name="project type")
    private String projectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false)
    private AccessLevel accessLevel = AccessLevel.PUBLIC; // Значение по умолчанию

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private Client client;

    @Column(name = "main_class")
    private String mainClass;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ProjectStruct> projectsStruct;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProjectTeam> projectTeams;
}
