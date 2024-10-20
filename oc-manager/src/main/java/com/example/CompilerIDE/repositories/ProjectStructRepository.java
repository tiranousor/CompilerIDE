package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectStructRepository extends JpaRepository<ProjectStruct, Long> {
    List<ProjectStruct> findByProject(Project project);
    Optional<ProjectStruct> findByProjectIdAndName(Long projectId, String name);
    void deleteByProjectIdAndName(Long projectId, String name);
    Optional<ProjectStruct> findByProjectIdAndPathAndType(int projectId, String path, String type);
    void deleteByProject(Project project);
    List<ProjectStruct> findByProjectAndType(Project project, String type);
}
