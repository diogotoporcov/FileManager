package com.diogotoporcov.filemanager.api.storage.adapter;

import com.diogotoporcov.filemanager.api.storage.config.ObjectStorageProperties;
import com.diogotoporcov.filemanager.api.storage.exception.StorageException;
import com.diogotoporcov.filemanager.api.storage.exception.StorageObjectNotFoundException;
import com.diogotoporcov.filemanager.api.storage.port.CreatePresignedDownloadUrlRequest;
import com.diogotoporcov.filemanager.api.storage.port.CreatePresignedDownloadUrlResponse;
import com.diogotoporcov.filemanager.api.storage.port.GetObjectRequest;
import com.diogotoporcov.filemanager.api.storage.port.GetObjectResponse;
import com.diogotoporcov.filemanager.api.storage.port.ObjectStoragePort;
import com.diogotoporcov.filemanager.api.storage.port.StoreObjectRequest;
import com.diogotoporcov.filemanager.api.storage.port.StoreObjectResponse;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class S3CompatibleObjectStorageAdapter implements ObjectStoragePort, InitializingBean {

    private final MinioClient minioClient;
    private final ObjectStorageProperties properties;

    @Override
    public void afterPropertiesSet() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucketName())
                    .build());

            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucketName())
                        .build());
            }
        } catch (Exception e) {
            throw new StorageException("Failed to initialize object storage bucket: " + properties.getBucketName(), e);
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
    public GetObjectResponse getObject(GetObjectRequest request) {
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(properties.getBucketName())
                    .object(request.getStoragePath());
            if (request.getRangeStart() != null) {
                builder.offset(request.getRangeStart());
            }
            if (request.getRangeLength() != null) {
                builder.length(request.getRangeLength());
            }

            io.minio.GetObjectResponse response = minioClient.getObject(builder.build());

            return GetObjectResponse.builder()
                    .content(response)
                    .contentLength(contentLength(response.headers().get("Content-Length")))
                    .contentType(response.headers().get("Content-Type"))
                    .etag(response.headers().get("ETag"))
                    .build();
        } catch (ErrorResponseException e) {
            if (isObjectNotFound(e)) {
                throw new StorageObjectNotFoundException("Storage object not found", e);
            }

            throw new StorageException("Failed to retrieve object: " + request.getStoragePath(), e);
        } catch (Exception e) {
            throw new StorageException("Failed to retrieve object: " + request.getStoragePath(), e);
        }
    }

    @Override
    public CreatePresignedDownloadUrlResponse createPresignedDownloadUrl(CreatePresignedDownloadUrlRequest request) {
        Instant expiresAt = Instant.now().plus(request.getTtl());
        try {
            Map<String, String> queryParams = new HashMap<>();
            if (request.getResponseContentDisposition() != null && !request.getResponseContentDisposition().isBlank()) {
                queryParams.put("response-content-disposition", request.getResponseContentDisposition());
            }
            if (request.getResponseContentType() != null && !request.getResponseContentType().isBlank()) {
                queryParams.put("response-content-type", request.getResponseContentType());
            }

            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.getBucketName())
                            .object(request.getStoragePath())
                            .expiry(Math.toIntExact(request.getTtl().toSeconds()))
                            .extraQueryParams(queryParams)
                            .build()
            );

            return CreatePresignedDownloadUrlResponse.builder()
                    .url(url)
                    .expiresAt(expiresAt)
                    .build();
        } catch (Exception e) {
            throw new StorageException("Failed to create presigned download URL", e);
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

    private long contentLength(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private boolean isObjectNotFound(ErrorResponseException exception) {
        String code = exception.errorResponse().code();

        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
    }
}
