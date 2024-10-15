package com.example.FileContainer.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;

@Service
public class FileService {

    private final Path rootLocation;

    public FileService(@Value("${file.storage.path}") String storagePath) {
        this.rootLocation = Paths.get(storagePath);
    }

    public void storeFile(String projectId, String path, MultipartFile file) throws IOException {
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        Path projectDir = rootLocation.resolve(projectId).resolve(path).normalize();

        Files.createDirectories(projectDir);

        try {
            if (file.isEmpty()) {
                throw new IOException("Failed to store empty file " + filename);
            }
            Path destinationFile = projectDir.resolve(filename);
            file.transferTo(destinationFile);
        } catch (IOException e) {
            throw new IOException("Failed to store file " + filename, e);
        }
    }

    public Resource loadFile(String projectId, String path, String filename) throws MalformedURLException {
        Path file = rootLocation.resolve(projectId).resolve(path).resolve(filename).normalize();
        Resource resource = new UrlResource(file.toUri());
        if (resource.exists() || resource.isReadable()) {
            return resource;
        } else {
            throw new RuntimeException("Could not read file: " + filename);
        }
    }

    public void deleteFile(String projectId, String path, String filename) throws IOException {
        Path file = rootLocation.resolve(projectId).resolve(path).resolve(filename).normalize();
        Files.deleteIfExists(file);
    }
}
