package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Integer> {
    Optional<Client> findByUsername(String username);

    Optional <Client> findByEmail(String email);

    public Client findByResetPasswordToken(String token);
    List<Client> findByUsernameContainingIgnoreCase(String username);
}
