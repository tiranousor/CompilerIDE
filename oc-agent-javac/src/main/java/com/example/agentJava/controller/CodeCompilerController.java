package com.example.agentJava.controller;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.providers.Project;
import com.example.agentJava.client.UserClient;
import com.example.agentJava.services.CreateFileService;
import com.example.agentJava.services.DockerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/compile")
public class CodeCompilerController {

    DockerService dockerService;
    CreateFileService createFileService;
    UserClient userClient;
    @Autowired
    public CodeCompilerController(DockerService dockerService, CreateFileService createFileService) {
        this.createFileService = createFileService;
        this.dockerService = dockerService;
    }

    // Класс для представления каждого файла, отправленного с фронта
    public static class FileData {
        public String fileName;
        public String content;
    }

    // Класс для представления всего запроса (содержит массив файлов и язык)
    public static class CompileRequest {
        public List<FileData> files;
        public String language;
    }

//    @PostMapping("/compile")
//    public ResponseEntity<?> compileCode(@RequestBody CompileRequest request) throws Exception {
//        String baseFolderPath = Paths.get(System.getProperty("user.dir"),  "oc-agent-java/Docker", request.language).toString();
//        String id = createFileService.saveFilesLocally(request.files, baseFolderPath);
//        String output = dockerService.runAndGetOutput(request.language, baseFolderPath, id);
//        System.out.println(output);
//        return ResponseEntity.status(HttpStatus.OK)
//                .header("Content-Type", "text/plain")
//                .body(output);
//    }
@PostMapping("/compile")
public ResponseEntity<?> compileCode(@RequestBody CompileRequest request, Authentication authentication) throws Exception {
    String uniqueId;
    Project savedProject;

    // Проверка авторизации
    if (authentication != null && authentication.isAuthenticated()) {
        String username = authentication.getName();
        Client client = userClient.getClientByUsername(username);

        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");
        }

        uniqueId = client.getId().toString();

        Project project = new Project();
        project.setClient(client);
//        project.setUuid(uniqueId); // UID проекта совпадает с UID клиента
        project.setName(request.files.get(0).fileName);
        project.setLanguage(request.language);
        savedProject = userClient.createProject(project);
    } else {
        uniqueId = UUID.randomUUID().toString();
        savedProject = null; // Нет сохранения проекта для гостя
    }

    // Сохраняем файлы для компиляции в папке, связанной с `uniqueId`
    String baseFolderPath = Paths.get(System.getProperty("user.dir"), "oc-agent-javac/Docker", request.language).toString();
    String id = createFileService.saveFilesLocally(request.files, baseFolderPath, uniqueId);

    // Обновляем информацию о проекте, если пользователь был авторизован
    if (savedProject != null) {
        savedProject.setReadMe("Files saved at path: " + baseFolderPath + "/" + id);
        userClient.createProject(savedProject);
    }

    // Выполняем компиляцию и возвращаем результат
    String output = dockerService.runAndGetOutput(request.language, baseFolderPath, id);
    System.out.println(output);

    return ResponseEntity.status(HttpStatus.OK)
            .header("Content-Type", "text/plain")
            .body(output);
}

}
