package com.example.CompilerIDE.providers;


import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

// Client.java
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="client")
public class Client {

    @Id
    @Column(name="user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username", unique = true)
    @NotBlank(message = "Имя пользователя не может быть пустым")
    private String username;

    @Column(name = "email", unique = true)
    @Email(message = "Введите действительный адрес электронной почты")
    @NotBlank(message = "Email не может быть пустым")
    private String email;

    @Column(name="active")
    private Boolean active;

    @NotEmpty(message = "Пароль не должен быть пустым")
    @Column(name = "password")
    private String password;

    @Column(name="avatarUrl")
    private String avatarUrl;

    @Column(name = "role")
    private String role = "ROLE_USER";

    @Column(name = "ex_profile_url", unique = false)
    private String githubProfile;

    @Column(name="about")
    private String about;

    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude // Исключаем список projects из toString()
    private List<Project> projects;
}

