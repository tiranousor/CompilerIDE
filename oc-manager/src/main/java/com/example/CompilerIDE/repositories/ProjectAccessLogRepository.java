package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectAccessLogRepository extends JpaRepository<ProjectAccessLog, Long> {
    // Сортировка по убыванию времени доступа (новые сначала)
    List<ProjectAccessLog> findByProjectOrderByAccessTimeDesc(Project project);
}
