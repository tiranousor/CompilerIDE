package com.example.CompilerIDE.services;

import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.io.InputStream;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    @Value("${minio.bucket-name}")
    private String bucketName;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public List<String> listFiles(String prefix) {
        List<String> fileNames = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    String objectName = item.objectName();
                    String relativePath = objectName.substring(prefix.length());
                    fileNames.add(relativePath);
                }
            }
        } catch (Exception e) {
            System.out.println("Ошибка при получении списка файлов из MinIO: " + e.getMessage());
        }
        return fileNames;
    }

    public void deleteAllObjectsWithPrefix(String prefix) throws Exception {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .recursive(true)
                        .build()
        );

        List<DeleteObject> objectsToDelete = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            objectsToDelete.add(new DeleteObject(item.objectName()));
        }

        if (!objectsToDelete.isEmpty()) {
            Iterable<Result<DeleteError>> resultsDelete = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(bucketName)
                            .objects(objectsToDelete)
                            .build()
            );

            for (Result<DeleteError> resultDelete : resultsDelete) {
                DeleteError error = resultDelete.get();
                logger.error("Ошибка при удалении объекта {}: {}", error.objectName(), error.message());
            }

            logger.info("Удалено {} объектов из MinIO с префиксом '{}'", objectsToDelete.size(), prefix);
        } else {
            logger.info("Нет объектов для удаления с префиксом '{}'", prefix);
        }
    }


    public void uploadFileContent(String objectKey, byte[] content) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(inputStream, content.length, -1)
                    .contentType("application/octet-stream")
                    .build();
            minioClient.putObject(putObjectArgs);
        }
    }

    public byte[] getFileContentAsBytes(String objectKey) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build()
        )) {
            return stream.readAllBytes();
        }
    }

    public void createBucket(String bucketName) {
        try {
            boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!isExist) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                System.out.println("Bucket '" + bucketName + "' успешно создан.");
            } else {
                System.out.println("Bucket '" + bucketName + "' уже существует.");
            }
        } catch (Exception e) {
            System.err.println("Ошибка при создании bucket: " + e.getMessage());
        }
    }
}
