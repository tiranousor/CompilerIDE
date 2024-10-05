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

    @Column(nullable = false, unique = true, name="uuid")
    private String uuid;
    @Column(nullable = false, unique = true, name="name")
    private String name;
    @Column(name="read me")
    private String readMe;
    @Column(nullable = false)
    private String language;
    @Column(name="ref to Git")
    private String refGit;
    @Column(name="project type")
    private String projectType;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Client client;
}
