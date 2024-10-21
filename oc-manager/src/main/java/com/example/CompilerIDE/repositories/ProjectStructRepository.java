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

    Optional<ProjectStruct> findByProjectAndName(Project project, String name);

    void deleteByProjectAndName(Project project, String name);


    Optional<ProjectStruct> findByProjectAndPath(Project project, String path);

    Optional<ProjectStruct> findByProjectAndPathAndType(Project project, String path, String type);

    void deleteByProject(Project project);

    List<ProjectStruct> findByProjectAndType(Project project, String type);

    // Методы для корневых файлов и дочерних элементов
    List<ProjectStruct> findByProjectAndPathNotContaining(Project project, String delimiter);

    List<ProjectStruct> findByProjectAndPathStartingWith(Project project, String pathPrefix);
}
