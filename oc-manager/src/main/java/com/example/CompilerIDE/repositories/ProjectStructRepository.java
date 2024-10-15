package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectStructRepository extends JpaRepository<ProjectStruct, Integer> {
    List<ProjectStruct> findByProject(Project project);
}
