package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUsername(String username);
//    List<Client> findAllById(List<Integer> clientId);
    List<Client> findByRoleOrderByUsernameAsc(String role);
    List<Client> findAllByOrderByCreatedAtDesc(); // Новые пользователи сначала
    List<Client> findAllByOrderByCreatedAtAsc();  // Старые пользователи сначала
    List<Client> findAllByOrderByLastLoginDesc();
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    Optional <Client> findByEmail(String email);
    Optional<Client> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    public Client findByResetPasswordToken(String token);
    List<Client> findByUsernameContainingIgnoreCase(String username);
}
