package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user to whom the notification is sent
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Client recipient;

    // The type of notification
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    // The message content
    @Column(name = "message", nullable = false)
    private String message;

    // Timestamp of the notification
    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    // Whether the notification has been read
    @Column(name = "read", nullable = false)
    private boolean read;
}