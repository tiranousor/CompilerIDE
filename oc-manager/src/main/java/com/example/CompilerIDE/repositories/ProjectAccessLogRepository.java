package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface ProjectAccessLogRepository extends JpaRepository<ProjectAccessLog, Long> {
    List<ProjectAccessLog> findByProject(Project project);
}