package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;

import java.util.List;

public interface ProjectStructRepository {
    void save(ProjectStruct projectStruct);

    List<ProjectStruct> findByProject(Project project);
}
