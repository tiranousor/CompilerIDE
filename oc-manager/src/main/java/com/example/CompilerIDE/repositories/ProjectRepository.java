package com.example.CompilerIDE.repositories;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface ProjectRepository extends JpaRepository<Project, Integer>{

    List<Project> findByClient(Client client);
//    List<Project> findByClientById(Integer client_id);
    List<Project> findByName(String name);

}
