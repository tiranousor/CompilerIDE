package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.LoginTimestamp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LoginTimestampRepository extends JpaRepository<LoginTimestamp, Long> {
    List<LoginTimestamp> findByClient(Client client);
    List<LoginTimestamp> findAllByClient(Client client);

    // Поиск записей входа по ID пользователя
    List<LoginTimestamp> findByClientId(Long clientId);

    LoginTimestamp findFirstByClientAndLogoutTimeIsNullOrderByLoginTimeDesc(Client client);

}