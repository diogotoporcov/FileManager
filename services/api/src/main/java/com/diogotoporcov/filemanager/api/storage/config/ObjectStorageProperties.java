package com.diogotoporcov.filemanager.api.storage.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "storage.object")
@Getter
@Setter
@Validated
public class ObjectStorageProperties {
    @NotBlank
    @URL
    private String endpoint;

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    @NotBlank
    private String bucketName;
}
