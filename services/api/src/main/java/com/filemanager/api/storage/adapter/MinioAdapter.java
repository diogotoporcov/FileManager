package com.filemanager.api.storage.adapter;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import com.filemanager.api.storage.config.MinioProperties;
import com.filemanager.api.storage.port.ObjectStoragePort;
import com.filemanager.api.storage.exception.StorageException;
import com.filemanager.api.storage.port.StoreObjectRequest;
import com.filemanager.api.storage.port.StoreObjectResponse;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class MinioAdapter implements ObjectStoragePort, InitializingBean {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public void afterPropertiesSet() {
        try {
            // Ensure the configured bucket exists at startup; create it if missing.
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucketName())
                    .build());

            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucketName())
                        .build());
            }
        } catch (Exception e) {
            throw new StorageException("Failed to initialize MinIO bucket: " + properties.getBucketName(), e);
        }
    }

    @Override
    public StoreObjectResponse putObject(StoreObjectRequest request) {
        try {
            ObjectWriteResponse response = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(request.getStoragePath())
                            .stream(request.getContent(), request.getSize(), -1)
                            .contentType(request.getContentType())
                            .build()
            );

            return StoreObjectResponse.builder()
                    .storagePath(response.object())
                    .etag(response.etag())
                    .versionId(response.versionId())
                    .build();
        } catch (Exception e) {
            throw new StorageException("Failed to store object: " + request.getStoragePath(), e);
        }
    }

    @Override
    public InputStream getObject(String storagePath) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(storagePath)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to retrieve object: " + storagePath, e);
        }
    }

    @Override
    public void deleteObject(String storagePath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(storagePath)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to delete object: " + storagePath, e);
        }
    }
}
