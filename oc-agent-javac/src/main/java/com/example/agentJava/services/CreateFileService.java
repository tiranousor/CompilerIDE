package com.example.agentJava.services;

import com.example.agentJava.controller.CodeCompilerController;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class CreateFileService {

    public String saveFilesLocally(List<CodeCompilerController.FileData> fileData, String baseFolderPath) throws IOException {
        // Уникальный ключ (UUID)
        String uniqueId = UUID.randomUUID().toString();

        String folderPath = baseFolderPath + "/" + uniqueId;

        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        for (CodeCompilerController.FileData file : fileData) {
            String fileName = file.fileName;
            String fileContent = file.content;

            // Создаем файл в папке
            File newFile = new File(folder, fileName);
            try (FileWriter writer = new FileWriter(newFile)) {
                writer.write(fileContent);
            }
        }
        return uniqueId;
    }
    // Метод для сохранения файлов в папке, идентифицированной по `uniqueId`
    public String saveFilesLocally(List<CodeCompilerController.FileData> fileData, String baseFolderPath, String uniqueId) throws IOException {
        // Путь к папке, включающий уникальный идентификатор пользователя
        String folderPath = baseFolderPath + "/" + uniqueId;

        // Создаем папку, если она еще не существует
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Сохраняем каждый файл в указанной папке
        for (CodeCompilerController.FileData file : fileData) {
            String fileName = file.fileName;
            String fileContent = file.content;

            // Создаем файл в папке
            File newFile = new File(folder, fileName);
            try (FileWriter writer = new FileWriter(newFile)) {
                writer.write(fileContent);
            }
        }
        return uniqueId;
    }


}