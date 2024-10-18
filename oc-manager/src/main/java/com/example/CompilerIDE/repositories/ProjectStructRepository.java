package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectStructRepository extends JpaRepository<ProjectStruct, Integer> {
    List<ProjectStruct> findByProject(Project project);
    Optional<ProjectStruct> findByProjectIdAndName(int projectId, String name);
    void deleteByProjectIdAndName(int projectId, String name);
}
