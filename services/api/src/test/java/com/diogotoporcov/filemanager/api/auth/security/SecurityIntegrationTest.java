package com.diogotoporcov.filemanager.api.auth.security;

import com.diogotoporcov.filemanager.api.application.CursorPage;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.file.application.FindFilesQuery;
import com.diogotoporcov.filemanager.api.file.application.FileService;
import com.diogotoporcov.filemanager.api.identity.application.IdentityResolutionService;
import com.diogotoporcov.filemanager.api.processing.application.AudioAnalysisResultCommand;
import com.diogotoporcov.filemanager.api.processing.application.ProcessingJobService;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import java.time.Instant;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String VALID_INTERNAL_TOKEN = "test-internal-token-123456789012";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private IdentityResolutionService identityResolutionService;

    @MockitoBean
    private ProcessingJobService processingJobService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedRequest_Returns401() throws Exception {
        mockMvc.perform(get("/files"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequest_UsesIdentityResolution() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@example.com").build();

        when(identityResolutionService.resolveUser(any())).thenReturn(user);
        when(fileService.searchFiles(any(FindFilesQuery.class), eq(userId)))
                .thenReturn(new CursorPage<>(java.util.List.of(), null, false, 0));

        mockMvc.perform(get("/files")
                        .with(jwt().jwt(builder -> builder.subject("sub-123"))))
                .andExpect(status().isOk());

        verify(identityResolutionService).resolveUser(any());
        verify(fileService).searchFiles(any(FindFilesQuery.class), eq(userId));
    }

    @Test
    void authenticatedRequest_IgnoresActorUserIdQueryParam() throws Exception {
        UUID actualUserId = UUID.randomUUID();
        UUID ignoredUserId = UUID.randomUUID();
        User user = User.builder().id(actualUserId).email("test@example.com").build();

        when(identityResolutionService.resolveUser(any())).thenReturn(user);
        when(fileService.searchFiles(any(FindFilesQuery.class), eq(actualUserId)))
                .thenReturn(new CursorPage<>(java.util.List.of(), null, false, 0));

        mockMvc.perform(get("/files")
                        .param("actorUserId", ignoredUserId.toString())
                        .with(jwt().jwt(builder -> builder.subject("sub-123"))))
                .andExpect(status().isOk());

        verify(fileService).searchFiles(any(FindFilesQuery.class), eq(actualUserId));
        verify(fileService, never()).searchFiles(any(FindFilesQuery.class), eq(ignoredUserId));
    }

    @Test
    void authenticatedRequest_BindsFindFilesQueryParams() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@example.com").build();

        when(identityResolutionService.resolveUser(any())).thenReturn(user);
        when(fileService.searchFiles(any(FindFilesQuery.class), eq(userId)))
                .thenReturn(new CursorPage<>(java.util.List.of(), null, false, 0));

        mockMvc.perform(get("/files")
                        .param("createdAtFrom", "2026-01-01T00:00:00Z")
                        .param("createdAtTo", "2026-02-01T00:00:00Z")
                        .param("sizeMin", "1024")
                        .param("sizeMax", "2048")
                        .param("mimeType", "image/jpeg")
                        .param("mimeType", "image/png")
                        .param("sort", "size,asc")
                        .param("limit", "100")
                        .with(jwt().jwt(builder -> builder.subject("sub-123"))))
                .andExpect(status().isOk());

        ArgumentCaptor<FindFilesQuery> queryCaptor = ArgumentCaptor.forClass(FindFilesQuery.class);
        verify(fileService).searchFiles(queryCaptor.capture(), eq(userId));
        FindFilesQuery query = queryCaptor.getValue();
        assertEquals("2026-01-01T00:00:00Z", query.getCreatedAtFrom());
        assertEquals("2026-02-01T00:00:00Z", query.getCreatedAtTo());
        assertEquals(1024L, query.getSizeMin());
        assertEquals(2048L, query.getSizeMax());
        assertEquals(List.of("image/jpeg", "image/png"), query.getMimeType());
        assertEquals("size,asc", query.getSort());
        assertEquals(100, query.getLimit());
    }

    @Test
    void authenticatedRequest_AccessDenied_Returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@example.com").build();

        when(identityResolutionService.resolveUser(any())).thenReturn(user);
        when(fileService.searchFiles(any(FindFilesQuery.class), eq(userId)))
                .thenThrow(new AccessDeniedException("Forbidden"));

        mockMvc.perform(get("/files")
                        .with(jwt().jwt(builder -> builder.subject("sub-123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpoint_PermitsAll() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessEndpoint_PermitsAll() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessEndpoint_PermitsAll() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoEndpoint_PermitsAll() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("filemanager-api"))
                .andExpect(jsonPath("$.app.role").value("metadata-api"));
    }

    @Test
    void envEndpoint_IsForbiddenOrNotFound() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsEndpoint_IsForbiddenOrNotFound() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoint_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(post("/internal/processing/jobs/" + UUID.randomUUID() + "/checksum-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoint_WithValidTokenAndInvalidBody_Returns400() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String invalidContent = String.format("{\"fileId\":\"%s\", \"sha256\":\"too-short\"}", fileId);

        mockMvc.perform(post("/internal/processing/jobs/" + jobId + "/checksum-result")
                        .header("Authorization", "Bearer " + VALID_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidContent))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalEndpoint_WithEncodedTraversalBypass_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/files/%2e%2e/internal/processing/jobs/" + UUID.randomUUID() + "/checksum-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalEndpoint_WithDoubleSlash_Returns401() throws Exception {
        mockMvc.perform(post("//internal/processing/jobs/" + UUID.randomUUID() + "/checksum-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoint_WithInvalidToken_Returns401() throws Exception {
        mockMvc.perform(post("/internal/processing/jobs/" + UUID.randomUUID() + "/checksum-result")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoint_WithMalformedHeader_Returns401() throws Exception {
        mockMvc.perform(post("/internal/processing/jobs/" + UUID.randomUUID() + "/checksum-result")
                        .header("Authorization", "InvalidFormat token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpoint_WithValidToken_PermitsAccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String content = String.format("{\"fileId\":\"%s\", \"sha256\":\"%s\"}", fileId, sha256);

        mockMvc.perform(post("/internal/processing/jobs/" + jobId + "/checksum-result")
                        .header("Authorization", "Bearer " + VALID_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());

        verify(processingJobService).handleChecksumResult(eq(jobId), eq(fileId), eq(sha256));
    }

    @Test
    void internalPhashResult_WithValidToken_PermitsAccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String phash = "fedcba9876543210";
        String content = String.format("{\"fileId\":\"%s\", \"phash\":\"%s\"}", fileId, phash);

        mockMvc.perform(post("/internal/processing/jobs/" + jobId + "/phash-result")
                        .header("Authorization", "Bearer " + VALID_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());

        verify(processingJobService).handlePhashResult(eq(jobId), eq(fileId), eq(phash));
    }

    @Test
    void internalEmbeddingResult_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(post("/internal/processing/jobs/" + UUID.randomUUID() + "/embedding-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEmbeddingResult_WithValidToken_PermitsAccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String content = String.format("""
                {"fileId":"%s","modelName":"openai/clip-vit-large-patch14","modelVersion":"1","dimension":2,"embedding":[0.1,0.2]}
                """, fileId);

        mockMvc.perform(post("/internal/processing/jobs/" + jobId + "/embedding-result")
                        .header("Authorization", "Bearer " + VALID_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());

        verify(processingJobService).handleEmbeddingResult(
                eq(jobId),
                eq(fileId),
                eq("openai/clip-vit-large-patch14"),
                eq("1"),
                eq(2),
                eq(List.of(0.1, 0.2)));
    }

    @Test
    void internalAudioAnalysisResult_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(post("/internal/processing/jobs/" + UUID.randomUUID() + "/audio-analysis-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalAudioAnalysisResult_WithValidToken_PermitsAccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String content = String.format("""
                {
                  "fileId":"%s",
                  "durationMs":1000,
                  "codec":"mp3",
                  "sampleRate":44100,
                  "channels":2,
                  "bitRate":128000,
                  "audioStreamIndex":0,
                  "containerFormat":"mp3",
                  "fingerprint":"12345ABC",
                  "fingerprintAlgorithm":"chromaprint",
                  "fingerprintVersion":"fpcalc",
                  "fingerprintDurationSeconds":60
                }
                """, fileId);

        mockMvc.perform(post("/internal/processing/jobs/" + jobId + "/audio-analysis-result")
                        .header("Authorization", "Bearer " + VALID_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());

        verify(processingJobService).handleAudioAnalysisResult(eq(jobId), any(AudioAnalysisResultCommand.class));
    }

    @Test
    void internalFailureReport_WithValidToken_PermitsAccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String error = "test error";
        String content = String.format("{\"fileId\":\"%s\", \"errorMessage\":\"%s\"}", fileId, error);

        mockMvc.perform(post("/internal/processing/jobs/" + jobId + "/failed")
                        .header("Authorization", "Bearer " + VALID_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());

        verify(processingJobService).handleProcessingFailure(eq(jobId), eq(fileId), eq(error));
    }

    @Test
    void internalToken_DoesNotAuthenticatePublicEndpoints() throws Exception {
        Jwt dummyJwt = Jwt.withTokenValue("test-internal-token")
                .header("alg", "none")
                .subject("test-internal-token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        when(jwtDecoder.decode("test-internal-token")).thenReturn(dummyJwt);
        when(identityResolutionService.resolveUser(dummyJwt)).thenThrow(new AccessDeniedException("Invalid user"));

        mockMvc.perform(get("/files")
                        .header("Authorization", "Bearer test-internal-token"))
                .andExpect(status().isForbidden());
    }
}
