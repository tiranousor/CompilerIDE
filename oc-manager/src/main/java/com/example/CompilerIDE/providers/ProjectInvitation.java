package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "project_invitations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "sender_id", "receiver_id"}))
public class ProjectInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Проект, к которому отправляется приглашение
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // Отправитель приглашения
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Client sender;

    // Получатель приглашения
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Client receiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "timestamp", nullable = false)
    private Timestamp timestamp;

    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}
