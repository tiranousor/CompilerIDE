package com.example.FileContainer.Controller;

import com.example.FileContainer.Services.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;

@RestController
@RequestMapping("/projects")
public class FileController {

    private final FileService fileService;

    @Autowired
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/{projectId}/files")
    public ResponseEntity<?> uploadFiles(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "path", defaultValue = "") String path) {
        try {
            fileService.storeFile(projectId, path, file);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to store file.");
        }
    }

    @GetMapping("/{projectId}/files")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String projectId,
            @RequestParam("path") String path,
            @RequestParam("filename") String filename) {
        try {
            Resource file = fileService.loadFile(projectId, path, filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                    .body(file);
        } catch (MalformedURLException e) {
            return ResponseEntity.status(404).body(null);
        }
    }

    @DeleteMapping("/{projectId}/files")
    public ResponseEntity<?> deleteFile(
            @PathVariable String projectId,
            @RequestParam("path") String path,
            @RequestParam("filename") String filename) {
        try {
            fileService.deleteFile(projectId, path, filename);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to delete file.");
        }
    }
}
