package com.example.CompilerIDE.config;

import com.example.CompilerIDE.providers.Client;
import com.example.CompilerIDE.services.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class BucketInitializer implements CommandLineRunner {

    private final MinioService minioService;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Autowired
    public BucketInitializer(MinioService minioService) {
        this.minioService = minioService;
    }

    @Override
    public void run(String... args) throws Exception {
        minioService.createBucket(bucketName);

    }
}
