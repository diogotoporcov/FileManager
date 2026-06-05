package com.filemanager.api.file.web;

import com.filemanager.api.auth.application.CurrentUserService;
import com.filemanager.api.file.application.FileService;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.identity.application.IdentityResolutionService;
import com.filemanager.api.processing.application.ProcessingJobService;
import com.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class FileDownloadSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private CurrentUserService currentUserService;

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
    @WithMockUser
    void downloadFile_NormalFilename_ShouldReturn200() throws Exception {
        UUID fileId = UUID.randomUUID();
        String filename = "test.txt";
        FileEntity file = FileEntity.builder()
                .id(fileId)
                .name(filename)
                .mimeType("text/plain")
                .build();

        when(fileService.getFileMetadata(any(), any())).thenReturn(file);
        when(fileService.downloadFile(any(), any())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename=\"test.txt\"")));
    }

    @Test
    @WithMockUser
    void downloadFile_WithCrlfInFilename_ShouldBeRejected() throws Exception {
        UUID fileId = UUID.randomUUID();
        String maliciousName = "normal.txt\"\r\nInjected-Header: value";
        FileEntity file = FileEntity.builder()
                .id(fileId)
                .name(maliciousName)
                .mimeType("text/plain")
                .build();

        when(fileService.getFileMetadata(any(), any())).thenReturn(file);
        when(fileService.downloadFile(any(), any())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Injected-Header"));
    }

    @Test
    @WithMockUser
    void downloadFile_WithQuotesInFilename_ShouldBeHandledSafely() throws Exception {
        UUID fileId = UUID.randomUUID();
        String filename = "file \"with\" quotes.txt";
        FileEntity file = FileEntity.builder()
                .id(fileId)
                .name(filename)
                .mimeType("text/plain")
                .build();

        when(fileService.getFileMetadata(any(), any())).thenReturn(file);
        when(fileService.downloadFile(any(), any())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename=")));
    }

    @Test
    @WithMockUser
    void downloadFile_WithUnicodeInFilename_ShouldBeEncoded() throws Exception {
        UUID fileId = UUID.randomUUID();
        String filename = "file-🚀.txt";
        FileEntity file = FileEntity.builder()
                .id(fileId)
                .name(filename)
                .mimeType("text/plain")
                .build();

        when(fileService.getFileMetadata(any(), any())).thenReturn(file);
        when(fileService.downloadFile(any(), any())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=")));
    }
}