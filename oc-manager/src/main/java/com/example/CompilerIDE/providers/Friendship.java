package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"client1_id", "client2_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // To avoid duplicate friendships, always store client1_id < client2_id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client1_id", nullable = false)
    private Client client1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client2_id", nullable = false)
    private Client client2;

    @Column(name = "since")
    private Timestamp since;
}
