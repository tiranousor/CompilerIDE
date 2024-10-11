package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, name="name")
    private String name;

    @Column(name="read_me")
    private String readMe;

    @Column(nullable = false)
    private String language;

    @Column(name="ref_to_git")
    private String refGit;

    @Column(name="project_type")
    private String projectType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // `nullable = true` для проектов гостей
    private Client client;
}
