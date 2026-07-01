package com.diogotoporcov.filemanager.api.storage.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3CompatibleObjectStorageConfig {

    @Bean
    public MinioClient minioClient(ObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
