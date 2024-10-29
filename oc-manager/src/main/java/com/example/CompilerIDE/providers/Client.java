package com.example.CompilerIDE.providers;


import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.Serializable;
@Entity
@Table(name="client")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Client implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name="user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "username")
    @NotBlank(message = "Имя пользователя не может быть пустым")
    private String username;

//    @Column(name = "last_login_time")
//    private Date lastLoginTime;

    // Связь с таблицей login_timestamps
    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<LoginTimestamp> loginTimes = new ArrayList<>();

    @Column(name = "email", unique = true)
    @Email(message = "Введите действительный адрес электронной почты")
    @NotBlank(message = "Email не может быть пустым")
    private String email;

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
    private List<Project> projects;
}
