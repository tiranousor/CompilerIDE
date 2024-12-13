package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.AccessLevel;
import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Integer>{

    List<Project> findByClient(Client client);
    List<Project> findByName(String name);
    Optional<Project> findByNameAndClient(String name, Client client);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Project> findByClientAndAccessLevel(Client viewedUser, AccessLevel accessLevel);
}
