package com.filemanager.gateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("fileManagerGatewayProperties")
@ConfigurationProperties(prefix = "filemanager.api")
@Getter
@Setter
@Validated
public class GatewayProperties {

    @NotBlank
    @URL
    private String baseUrl;
}
