package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.LoginTimestamp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginTimestampRepository extends JpaRepository<LoginTimestamp, Long> {
    List<LoginTimestamp> findByClient(Client client);
    List<LoginTimestamp> findAllByClient(Client client);
}
