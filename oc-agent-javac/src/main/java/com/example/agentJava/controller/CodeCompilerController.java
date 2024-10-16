package com.example.agentJava.controller;

import com.example.agentJava.services.CreateFileService;
import com.example.agentJava.services.DockerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CodeCompilerController {

    DockerService dockerService;
    CreateFileService createFileService;

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

    @PostMapping("/compile")
    public ResponseEntity<?> compileCode(@RequestBody CompileRequest request) throws Exception {
        String baseFolderPath = Paths.get(System.getProperty("user.dir"),  "Docker", request.language).toString();
        String id = createFileService.saveFilesLocally(request.files, baseFolderPath);
        String output = dockerService.runAndGetOutput(request.language, baseFolderPath, id);
        System.out.println(output);
        return ResponseEntity.status(HttpStatus.OK)
                .header("Content-Type", "text/plain")
                .body(output);
    }
}
