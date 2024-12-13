package com.example.CompilerIDE.providers;


import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.io.Serializable;
@Entity
@Table(name="client")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Client implements Serializable {

    @Id
    @Column(name="user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username")
    @NotBlank(message = "Имя пользователя не может быть пустым")
    private String username;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

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
    @OneToMany(mappedBy = "sender", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<FriendRequest> sentFriendRequests = new ArrayList<>();

    @OneToMany(mappedBy = "receiver", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<FriendRequest> receivedFriendRequests = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(
            name = "friendships",
            joinColumns = @JoinColumn(name = "client1_id"),
            inverseJoinColumns = @JoinColumn(name = "client2_id")
    )
    private Set<Client> friends = new HashSet<>();
    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ProjectTeam> projectTeams;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
