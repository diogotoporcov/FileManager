package com.filemanager.api.config;

import com.filemanager.api.event.FileProcessingRequestedEvent;
import com.filemanager.api.service.FileService;
import com.filemanager.api.service.IdentityResolutionService;
import com.filemanager.api.service.ProcessingJobService;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
public class OpenApiIntegrationTest {

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
    void openApiEndpointIsAvailableAndDocumentedCorrectly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.info.title").value("FileManager API"))
                // Verify all public paths
                .andExpect(jsonPath("$.paths['/files']").exists())
                .andExpect(jsonPath("$.paths['/files/{fileId}']").exists())
                .andExpect(jsonPath("$.paths['/files/{fileId}/download']").exists())
                .andExpect(jsonPath("$.paths['/files/{fileId}/duplicates']").exists())
                .andExpect(jsonPath("$.paths['/duplicate-candidates']").exists())
                .andExpect(jsonPath("$.paths['/files/{fileId}/processing-jobs']").exists())
                .andExpect(jsonPath("$.paths['/files/{fileId}/processing-status']").exists())
                // Verify security
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                // Verify internal path is NOT included in default/public docs
                .andExpect(jsonPath("$.paths['/internal/processing/jobs']").doesNotExist())
                .andExpect(jsonPath("$.paths['/internal/processing/jobs/{jobId}/checksum-result']").doesNotExist());
    }

    @Test
    void publicApiGroupIsAvailableAndExcludesInternal() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/files']").exists())
                .andExpect(jsonPath("$.paths['/internal/processing/jobs/{jobId}/checksum-result']").doesNotExist());
    }

    @Test
    void swaggerUiIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }

    @Test
    void apiDocsIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
