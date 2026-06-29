package com.diogotoporcov.filemanager.gateway.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "filemanager.gateway.rate-limit")
@Getter
@Setter
@Validated
public class RateLimitProperties {

    private boolean enabled = true;

    @Min(1)
    private int replenishRate = 20;

    @Min(1)
    private int burstCapacity = 40;

    @Min(1)
    private int requestedTokens = 1;

    private boolean forwardedHeadersEnabled = true;

    private List<@NotBlank String> trustedProxyCidrs = new ArrayList<>(List.of(
            "127.0.0.1/32",
            "::1/128",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16"
    ));
}
