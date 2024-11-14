// UnbanRequest.java
package com.example.CompilerIDE.providers;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.io.Serializable;
@Entity
@Table(name="UnbanRequest")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnbanRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;

    private LocalDateTime requestTime;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    // Getters and Setters
    // ...
}
