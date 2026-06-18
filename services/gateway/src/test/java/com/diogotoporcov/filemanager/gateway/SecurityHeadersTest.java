package com.diogotoporcov.filemanager.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHeadersTest {

    private static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    @Autowired
    private ApplicationContext context;

    private WebTestClient webClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("filemanager.api.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void setup() {
        this.webClient = WebTestClient.bindToApplicationContext(this.context)
                .configureClient()
                .build();
        wireMockServer.resetAll();
    }

    @Test
    void actuatorHealthIncludesSecurityHeaders() {
        webClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
    }

    @Test
    void actuatorInfoIncludesSecurityHeaders() {
        webClient.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
    }

    @Test
    void routedRequestIncludesSecurityHeaders() {
        stubFor(get(urlEqualTo("/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webClient.get().uri("/api/v1/files")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
    }

    @Test
    void blockedInternalRequestIncludesSecurityHeaders() {
        webClient.get().uri("/api/v1/internal/any")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
    }

    @Test
    void unroutedRequestIncludesSecurityHeaders() {
        webClient.get().uri("/api/v2/any")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer");
    }

    @Test
    void downloadPreservesContentDisposition() {
        stubFor(get(urlEqualTo("/files/123/download"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Disposition", "attachment; filename=\"test.txt\"")
                        .withHeader("Content-Type", "text/plain")
                        .withBody("file-content")));

        webClient.get().uri("/api/v1/files/123/download")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Disposition", "attachment; filename=\"test.txt\"")
                .expectHeader().valueEquals("Content-Type", "text/plain")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody(String.class).isEqualTo("file-content");
    }

    @Test
    void doesNotDuplicateHeadersIfAlreadyPresent() {
        stubFor(get(urlEqualTo("/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Content-Type-Options", "nosniff")
                        .withBody("[]")));

        webClient.get().uri("/api/v1/files")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody()
                .consumeWith(result -> assertThat(result.getResponseHeaders().get("X-Content-Type-Options")).hasSize(1));
    }
}
