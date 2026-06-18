package com.diogotoporcov.filemanager.api.exception;

import com.diogotoporcov.filemanager.api.identity.application.IdentityResolutionService;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.storage.exception.StorageException;
import com.diogotoporcov.filemanager.api.file.application.search.SearchValidationException;
import com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery;
import com.diogotoporcov.filemanager.api.file.application.FileService;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ErrorHandlingIntegrationTest {

    private static final String VALID_INTERNAL_TOKEN = "test-internal-token-123456789012";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private IdentityResolutionService identityResolutionService;

    @MockitoBean
    private MinioClient minioClient;

    @MockitoBean
    private KafkaTemplate<String, FileProcessingRequestedEvent> kafkaTemplate;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        User user = User.builder().id(UUID.randomUUID()).email("test@example.com").build();
        when(identityResolutionService.resolveUser(any())).thenReturn(user);
    }

    @Test
    void resourceNotFound_Returns404() throws Exception {
        when(fileService.getFileMetadata(any(), any()))
                .thenThrow(new ResourceNotFoundException("File not found"));

        mockMvc.perform(get("/files/" + UUID.randomUUID())
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File not found"));
    }

    @Test
    void accessDenied_Returns403() throws Exception {
        when(fileService.getFileMetadata(any(), any()))
                .thenThrow(new AccessDeniedException("Forbidden access"));

        mockMvc.perform(get("/files/" + UUID.randomUUID())
                        .with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden access"));
    }

    @Test
    void storageError_Returns500WithSafeMessage() throws Exception {
        when(fileService.getFileMetadata(any(), any()))
                .thenThrow(new StorageException("Raw minio error details here"));

        mockMvc.perform(get("/files/" + UUID.randomUUID())
                        .with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("A storage error occurred while processing your request."));
    }

    @Test
    void invalidFileSearchParam_Returns400() throws Exception {
        when(fileService.searchFiles(any(FileSearchQuery.class), any()))
                .thenThrow(new SearchValidationException("Unsupported sort field: storagePath"));

        mockMvc.perform(get("/files")
                        .param("sort", "storagePath,desc")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported sort field: storagePath"));
    }

    @Test
    void unexpectedException_Returns500WithGenericMessage() throws Exception {
        when(fileService.getFileMetadata(any(), any()))
                .thenThrow(new RuntimeException("Database is down or something else"));

        mockMvc.perform(get("/files/" + UUID.randomUUID())
                        .with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("An unexpected internal server error occurred."));
    }

    @Test
    void internalWorkerEndpointInvalidBody_Returns400() throws Exception {
        mockMvc.perform(post("/internal/processing/jobs/" + UUID.randomUUID() + "/checksum-result")
                        .header("Authorization", "Bearer " + VALID_INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details").value(org.hamcrest.Matchers.containsString("sha256:")));
    }

    @Test
    void internalWorkerEndpointInvalidToken_Returns401() throws Exception {
        mockMvc.perform(post("/internal/processing/jobs/" + UUID.randomUUID() + "/checksum-result")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + UUID.randomUUID() + "\", \"sha256\":\"abc\"}"))
                .andExpect(status().isUnauthorized());
    }
}
