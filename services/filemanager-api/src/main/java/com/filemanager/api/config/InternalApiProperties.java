package com.filemanager.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "filemanager.internal")
@Getter
@Setter
@Validated
public class InternalApiProperties {
    @NotBlank
    private String apiToken;
}
