package com.example.CompilerIDE.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import feign.Headers;

@FeignClient(name = "fileStorageClient", url = "http://localhost:8081") // Укажите URL вашего File Storage Server
public interface FileStorageClient {

    @PostMapping(value = "/projects/{projectId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    void uploadFile(@PathVariable("projectId") String projectId,
                    @RequestPart("file") MultipartFile file,
                    @RequestParam(value = "path", defaultValue = "") String path);

    @GetMapping("/projects/{projectId}/files")
    byte[] downloadFile(@PathVariable("projectId") String projectId,
                        @RequestParam("path") String path,
                        @RequestParam("filename") String filename);

    @DeleteMapping("/projects/{projectId}/files")
    void deleteFile(@PathVariable("projectId") String projectId,
                    @RequestParam("path") String path,
                    @RequestParam("filename") String filename);
}
