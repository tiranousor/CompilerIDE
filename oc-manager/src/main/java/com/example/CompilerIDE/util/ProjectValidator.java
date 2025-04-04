package com.example.CompilerIDE.util;


import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ProjectValidator implements Validator {
    private final ProjectService projectService;

    @Autowired
    public ProjectValidator(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return Project.class.equals(clazz);
    }
    @Override
    public void validate(Object target, Errors errors) {
        Project project = (Project) target;

        if (projectService.findByNameAndClient(project.getName(), project.getClient()).isPresent()) {
            errors.rejectValue("name", "error.name", "Проект с таким именем уже существует");
        }

    }

}
