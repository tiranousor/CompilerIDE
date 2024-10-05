package com.example.CompilerIDE.providers;


import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Table(name="client")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Client {

    @Id
    @Column(name="user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name="username")
    @NotBlank(message = "Username is mandatory")
    private String username;
    @Column(name="active")
    private Boolean active;
    @Column(name="email")
    @Email(message = "Please enter a valid email address")
    @NotBlank(message = "Email is mandatory")
    private String email;
    @NotEmpty(message = "Пароль не должен быть пустым")
    @Column(name = "password")
    private String password;
    @Column(name="avatarUrl")
    private String avatarUrl;
    @Column(name = "role")
    private String role = "ROLE_USER";
    @Column( name = "ex_profile_url", unique = false)
    private String githubProfile;
    @Column(name="about")
    private String about;
    @Column(name = "reset_password_token")
    private String resetPasswordToken;
    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Project> projects;
}
