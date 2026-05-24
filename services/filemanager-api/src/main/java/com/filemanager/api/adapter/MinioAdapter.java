package com.filemanager.api.adapter;

import com.filemanager.api.config.MinioProperties;
import com.filemanager.api.port.ObjectStoragePort;
import com.filemanager.api.port.StoreObjectRequest;
import com.filemanager.api.port.StoredObject;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class MinioAdapter implements ObjectStoragePort {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    @SneakyThrows
    public StoredObject putObject(StoreObjectRequest request) {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(properties.getBucketName())
                        .object(request.getStoragePath())
                        .stream(request.getContent(), request.getSize(), -1)
                        .contentType(request.getContentType())
                        .build()
        );

        return StoredObject.builder()
                .storagePath(request.getStoragePath())
                .bucket(properties.getBucketName())
                .build();
    }

    @Override
    @SneakyThrows
    public InputStream getObject(String storagePath) {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.getBucketName())
                        .object(storagePath)
                        .build()
        );
    }

    @Override
    @SneakyThrows
    public void deleteObject(String storagePath) {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(properties.getBucketName())
                        .object(storagePath)
                        .build()
        );
    }
}
