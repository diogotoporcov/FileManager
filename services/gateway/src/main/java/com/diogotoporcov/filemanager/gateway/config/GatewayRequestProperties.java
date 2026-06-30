package com.diogotoporcov.filemanager.gateway.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "filemanager.gateway")
@Getter
@Setter
@Validated
public class GatewayRequestProperties {

    @NotNull
    private DataSize maxRequestSize = DataSize.ofMegabytes(100);
}
