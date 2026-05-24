package com.filemanager.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    private static WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

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
        this.webClient = WebTestClient.bindToApplicationContext(this.context).build();
        wireMockServer.resetAll();
    }

    @Test
    void healthEndpointWorks() {
        webClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void routesApiV1FilesToBackendFiles() {
        stubFor(get(urlEqualTo("/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        webClient.get().uri("/api/v1/files")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");

        verify(getRequestedFor(urlEqualTo("/files")));
    }

    @Test
    void routesApiV1FilesWithIdToBackend() {
        stubFor(get(urlEqualTo("/files/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\"}")));

        webClient.get().uri("/api/v1/files/123")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo("123");

        verify(getRequestedFor(urlEqualTo("/files/123")));
    }

    @Test
    void routesApiV1FilesDownloadToBackend() {
        stubFor(get(urlEqualTo("/files/123/download"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("file-content")));

        webClient.get().uri("/api/v1/files/123/download")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("file-content");

        verify(getRequestedFor(urlEqualTo("/files/123/download")));
    }

    @Test
    void preservesQueryParams() {
        stubFor(get(urlEqualTo("/files?name=test"))
                .willReturn(aResponse().withStatus(200)));

        webClient.get().uri("/api/v1/files?name=test")
                .exchange()
                .expectStatus().isOk();

        verify(getRequestedFor(urlEqualTo("/files?name=test")));
    }

    @Test
    void preservesRequestMethodAndBody() {
        stubFor(post(urlEqualTo("/files"))
                .withRequestBody(equalTo("{\"name\":\"test\"}"))
                .willReturn(aResponse().withStatus(201)));

        webClient.post().uri("/api/v1/files")
                .bodyValue("{\"name\":\"test\"}")
                .exchange()
                .expectStatus().isCreated();

        verify(postRequestedFor(urlEqualTo("/files")));
    }

    @Test
    void routesMultipartUpload() {
        stubFor(post(urlEqualTo("/files"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .willReturn(aResponse().withStatus(201)));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", "test-content").filename("test.txt");

        webClient.post().uri("/api/v1/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated();

        verify(postRequestedFor(urlEqualTo("/files")));
    }

    @Test
    void blocksInternalEndpoints() {
        // Path /internal/**
        webClient.get().uri("/internal/any")
                .exchange()
                .expectStatus().isNotFound();

        // Path /api/v1/internal/**
        webClient.get().uri("/api/v1/internal/any")
                .exchange()
                .expectStatus().isNotFound();

        // Ensure nothing reached the backend
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void doesNotRouteApiV2ByDefault() {
        webClient.get().uri("/api/v2/files")
                .exchange()
                .expectStatus().isNotFound();

        verify(0, anyRequestedFor(anyUrl()));
    }
}
