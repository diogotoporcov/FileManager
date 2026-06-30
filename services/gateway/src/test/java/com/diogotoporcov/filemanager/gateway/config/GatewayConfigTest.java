package com.diogotoporcov.filemanager.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayConfigTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private GatewayProperties gatewayProperties;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("filemanager.api.base-url", () -> "http://localhost:8081");
    }

    @Test
    void gatewayProperties_LoadedAndValidated() {
        assertThat(gatewayProperties.getBaseUrl()).isEqualTo("http://localhost:8081");
    }

    @Test
    void gatewayTimeoutsAndMaxRequestSize_Configured() {
        // Connect timeout
        String connectTimeout = context.getEnvironment().getProperty("spring.cloud.gateway.server.webflux.httpclient.connect-timeout");
        assertThat(connectTimeout).isNotNull();
        assertThat(Integer.parseInt(connectTimeout)).isPositive();

        // Response timeout
        String responseTimeout = context.getEnvironment().getProperty("spring.cloud.gateway.server.webflux.httpclient.response-timeout");
        assertThat(responseTimeout).isNotNull();
        Duration duration = DurationStyle.detectAndParse(responseTimeout);
        assertThat(duration).isPositive();

        // Max request size
        String maxRequestSize = context.getEnvironment().getProperty("filemanager.gateway.max-request-size");
        assertThat(maxRequestSize).isNotNull();
    }
}
