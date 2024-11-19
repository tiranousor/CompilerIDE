package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectStructRepository extends JpaRepository<ProjectStruct, Long> {

    List<ProjectStruct> findByProject(Project project);

    Optional<ProjectStruct> findByProjectAndPath(Project project, String path);

    List<ProjectStruct> findByProjectAndType(Project project, String type);

    List<ProjectStruct> findByProjectIdAndType(Integer projectId, String file);
}
