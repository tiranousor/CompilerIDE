package com.example.CompilerIDE.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileStorageService {

    @Value("${upload.directory}")
    private String uploadDir; // Directory to save uploaded files

    public String storeFile(MultipartFile file) throws IOException {
        File directory = new File(uploadDir);
        if (!directory.exists()) {
            directory.mkdirs();  // Создает директорию, если её нет
        }

        String fileName = file.getOriginalFilename();
        Path path = Paths.get(uploadDir + File.separator + fileName);
        Files.copy(file.getInputStream(), path);
        return path.toString(); // or return URL if needed
    }
}
