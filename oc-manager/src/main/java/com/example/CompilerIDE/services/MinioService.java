package com.example.CompilerIDE.services;

import io.minio.*;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.io.InputStream;

@Service
public class MinioService {

    private final MinioClient minioClient;

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
                fileNames.add(item.objectName());
            }
        } catch (Exception e) {
            System.out.println("Error listing files in MinIO " + e.getMessage());
        }
        return fileNames;
    }

    // Создание папки
    public void createFolder(String objectKey) throws Exception {
        if (!objectKey.endsWith("/")) {
            objectKey += "/";
        }
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(new java.io.ByteArrayInputStream(new byte[0]), 0, -1)
                        .contentType("application/x-directory")
                        .build()
        );
    }

    // Создание файла
    public void createFile(String objectKey, String content) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(new java.io.ByteArrayInputStream(content.getBytes()), content.length(), -1)
                        .contentType("text/plain")
                        .build()
        );
    }

    // Переименование объекта
    public void renameObject(String oldObjectKey, String newObjectKey) throws Exception {
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(bucketName)
                        .object(newObjectKey)
                        .source(
                                CopySource.builder()
                                        .bucket(bucketName)
                                        .object(oldObjectKey)
                                        .build()
                        )
                        .build()
        );
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(oldObjectKey)
                        .build()
        );
    }

    // Удаление объекта
    public void deleteObject(String objectKey) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build()
        );
    }

    // Получение содержимого файла
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

    // Загрузка файла в MinIO
    public void uploadFile(String objectKey, InputStream inputStream, long size, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .stream(inputStream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    }

    // Удаление файла (обёртка для deleteObject)
    public void deleteFile(String objectKey) throws Exception {
        deleteObject(objectKey);
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
