package com.example.CompilerIDE.services;

import com.example.CompilerIDE.client.FileStorageClient;
import com.example.CompilerIDE.providers.Project;
import com.example.CompilerIDE.providers.ProjectStruct;
import com.example.CompilerIDE.repositories.ProjectStructRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.List;

@Service
public class CompilationService {

    private final FileStorageClient fileStorageClient;
    private final ProjectStructRepository projectStructRepository;

    @Autowired
    public CompilationService(FileStorageClient fileStorageClient,
                              ProjectStructRepository projectStructRepository) {
        this.fileStorageClient = fileStorageClient;
        this.projectStructRepository = projectStructRepository;
    }

    public String compileProject(Project project) throws IOException, InterruptedException {
        // Создаем временную директорию для компиляции
        Path tempDir = Files.createTempDirectory("project_" + project.getId());

        // Получаем файлы проекта из базы данных
        List<ProjectStruct> projectFiles = projectStructRepository.findByProject(project);

        // Загружаем файлы с File Storage Server и сохраняем во временную директорию
        for (ProjectStruct fileStruct : projectFiles) {
            byte[] fileContent = fileStorageClient.downloadFile(
                    project.getId().toString(),
                    fileStruct.getPath(),
                    fileStruct.getName()
            );
            Path filePath = tempDir.resolve(fileStruct.getPath()).resolve(fileStruct.getName());
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, fileContent);
        }

        // Запускаем компиляцию
        ProcessBuilder processBuilder = new ProcessBuilder("javac", "Main.java");
        processBuilder.directory(tempDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        // Удаляем временную директорию
        deleteDirectory(tempDir.toFile());

        // Возвращаем результат компиляции
        return output.toString();
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
