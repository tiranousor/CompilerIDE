package com.example.CompilerIDE.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MinioService {

    private final S3Client s3Client;

    @Value("${minio.bucket-name}")
    private String defaultBucketName;

    @Autowired
    public MinioService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void uploadFile(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(defaultBucketName)
                .key(objectKey)
                .contentLength(contentLength)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
    }

    public List<String> listFiles(String prefix) {
        ListObjectsV2Request listObjectsReqManual = ListObjectsV2Request.builder()
                .bucket(defaultBucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response listObjResponse = s3Client.listObjectsV2(listObjectsReqManual);
        List<String> fileNames = new ArrayList<>();
        for (S3Object content : listObjResponse.contents()) {
            fileNames.add(content.key().substring(prefix.length()));
        }
        return fileNames;
    }
    public String getFileContent(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(defaultBucketName)
                .key(objectKey)
                .build();

        try (InputStream inputStream = s3Client.getObject(getObjectRequest)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
//    public InputStream downloadFile(String bucketName, String objectKey) {
//        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
//                .bucket(bucketName)
//                .key(objectKey)
//                .build();
//
//        return s3Client.getObject(getObjectRequest);
//    }

    public void createBucket(String bucketName) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            // Бакет уже существует
        }
    }
}
