package com.example.CompilerIDE.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.services.ClientService;
import com.example.CompilerIDE.services.CompilationService;
import com.example.CompilerIDE.services.MinioService; // Добавлено
import com.example.CompilerIDE.services.ProjectService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*; // Изменено
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController // Изменено с @Controller на @RestController
@RequestMapping("/projects") // Добавлено
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;
    private final CompilationService compilationService;
    private final MinioService minioService; // Добавлено
    @Value("${minio.bucket-name}")
    private String bucketName; // Убедитесь, что бакет создан

    public ProjectController(ProjectService projectService,
                             ClientService clientService,
                             CompilationService compilationService,
                             MinioService minioService) { // Добавлено
        this.projectService = projectService;
        this.clientService = clientService;
        this.compilationService = compilationService;
        this.minioService = minioService; // Добавлено
    }

    // Отображение формы редактирования проекта
    @GetMapping("/edit/{id}")
    public String editProjectForm(@PathVariable("id") int projectId, Model model, Authentication authentication) {
        // Получаем текущего пользователя
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());

        if (clientOpt.isEmpty()) {
            return "redirect:/login";
        }

        Client client = clientOpt.get();

        // Находим проект по ID
        Optional<Project> projectOpt = projectService.findById(projectId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            // Проверяем, принадлежит ли проект текущему пользователю
            if (project.getClient().getId().equals(client.getId())) {
                model.addAttribute("project", project);
                return "edit_project_form"; // Название шаблона для формы редактирования
            } else {
                // Если проект не принадлежит пользователю, перенаправляем на профиль
                return "redirect:/userProfile";
            }
        } else {
            return "redirect:/userProfile";
        }
    }

    // Обработка данных после отправки формы редактирования
    @PostMapping("/edit/{id}")
    public String updateProject(@PathVariable("id") int projectId,
                                @Valid @ModelAttribute("project") Project projectForm,
                                BindingResult bindingResult,
                                Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "edit_project_form"; // Возвращаем обратно на форму, если есть ошибки
        }

        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return "redirect:/login";
        }

        Client client = clientOpt.get();

        Optional<Project> projectOpt = projectService.findById(projectId);

        if (projectOpt.isPresent()) {
            Project existingProject = projectOpt.get();
            if (existingProject.getClient().getId().equals(client.getId())) {
                existingProject.setName(projectForm.getName());
                existingProject.setLanguage(projectForm.getLanguage());
                existingProject.setReadMe(projectForm.getReadMe());
                existingProject.setRefGit(projectForm.getRefGit());
                existingProject.setProjectType(projectForm.getProjectType());
                projectService.save(existingProject);
            }
        }
        return "redirect:/userProfile";
    }

    // Сохранение файлов проекта с использованием MinIO
    @PostMapping("/{projectId}/save")
    public ResponseEntity<?> saveProjectFiles(@PathVariable int projectId,
                                              @RequestParam("files") List<MultipartFile> files,
                                              Authentication authentication) {
        // Проверяем, авторизован ли пользователь
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        // Находим проект
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();

        // Проверяем, принадлежит ли проект текущему пользователю
        if (!project.getClient().getId().equals(client.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        try {
            // Путь в MinIO для сохранения файлов проекта
            String projectPath = "projects/" + projectId + "/";

            // Создаём бакет, если необходимо (бакет может быть общим для всех проектов)
            minioService.createBucket(bucketName);

            // Сохраняем каждый файл в MinIO
            for (MultipartFile file : files) {
                String objectKey = projectPath + file.getOriginalFilename();

                minioService.uploadFile(
                        objectKey,
                        file.getInputStream(),
                        file.getSize(),
                        file.getContentType()
                );
            }

            return ResponseEntity.ok("Проект успешно сохранён");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при сохранении проекта");
        }
    }

    // Компиляция проекта
    @PostMapping("/{projectId}/compile")
    public ResponseEntity<?> compileProject(@PathVariable int projectId,
                                            Authentication authentication) {
        // Проверяем, авторизован ли пользователь
        Optional<Client> clientOpt = clientService.findByUsername(authentication.getName());
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Пользователь не авторизован");
        }

        Client client = clientOpt.get();

        // Находим проект
        Optional<Project> projectOpt = projectService.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Проект не найден");
        }

        Project project = projectOpt.get();

        // Проверяем, принадлежит ли проект текущему пользователю
        if (!project.getClient().getId().equals(client.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("У вас нет доступа к этому проекту");
        }

        try {
            // Компиляция проекта
            String compilationResult = compilationService.compileProject(project);

            return ResponseEntity.ok(compilationResult);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при компиляции проекта");
        }
    }
}
