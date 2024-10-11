package com.example.CompilerIDE.repositories;


import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectsStruct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectsStructRepository extends JpaRepository<ProjectsStruct, Integer> {
    List<ProjectsStruct> findByProject(Project project);
}