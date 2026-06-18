package com.diogotoporcov.filemanager.api.file.web;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.file.application.FileDownload;
import com.diogotoporcov.filemanager.api.file.application.InvalidDownloadRangeException;
import com.diogotoporcov.filemanager.api.file.application.PresignedDownloadUrl;
import com.diogotoporcov.filemanager.api.file.application.FileService;
import com.diogotoporcov.filemanager.api.identity.application.IdentityResolutionService;
import com.diogotoporcov.filemanager.api.processing.application.ProcessingJobService;
import com.diogotoporcov.filemanager.api.processing.messaging.FileProcessingRequestedEvent;
import com.diogotoporcov.filemanager.api.storage.exception.StorageObjectNotFoundException;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
        when(currentUserService.getCurrentUserId()).thenReturn(UUID.randomUUID());
    }

    @Test
    @WithMockUser
    void downloadFileNormalFilenameReturnsStreamingHeaders() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.openDownload(eq(fileId), any(), nullable(String.class)))
                .thenReturn(download("test.txt", "text/plain", "content", "etag-123"));

        MvcResult result = mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string("content"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/plain")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "7"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-123\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename=\"test.txt\"")));
    }

    @Test
    @WithMockUser
    void downloadFileWithSpecificAcceptHeaderReturnsStoredContentType() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.openDownload(eq(fileId), any(), nullable(String.class)))
                .thenReturn(download("video.mp4", "video/mp4", "content"));

        MvcResult result = mockMvc.perform(get("/files/" + fileId + "/download")
                        .accept(MediaType.parseMediaType("video/mp4")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("video/mp4")))
                .andExpect(content().string("content"));
    }

    @Test
    @WithMockUser
    void downloadFileUnknownMimeFallsBackToOctetStream() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.openDownload(eq(fileId), any(), nullable(String.class)))
                .thenReturn(download("test.bin", "not a media type", "content"));

        MvcResult result = mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/octet-stream")));
    }

    @Test
    @WithMockUser
    void downloadFileWithCrlfInFilenameSanitizesHeader() throws Exception {
        UUID fileId = UUID.randomUUID();
        String maliciousName = "normal.txt\"\r\nInjected-Header: value";
        when(fileService.openDownload(eq(fileId), any(), nullable(String.class)))
                .thenReturn(download(maliciousName, "text/plain", "content"));

        MvcResult result = mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Injected-Header"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("\r"))))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("\n"))));
    }

    @Test
    @WithMockUser
    void downloadFileWithUnicodeFilenameUsesEncodedFilename() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.openDownload(eq(fileId), any(), nullable(String.class)))
                .thenReturn(download("file-ä.txt", "text/plain", "content"));

        MvcResult result = mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=")));
    }

    @Test
    @WithMockUser
    void downloadFileValidRangeReturnsPartialContentHeaders() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.openDownload(eq(fileId), any(), eq("bytes=2-5")))
                .thenReturn(FileDownload.builder()
                        .name("video.mp4")
                        .mimeType("video/mp4")
                        .completeSize(10L)
                        .contentLength(4L)
                        .rangeStart(2L)
                        .rangeEnd(5L)
                        .content(new ByteArrayInputStream("cdef".getBytes()))
                        .build());

        MvcResult result = mockMvc.perform(get("/files/" + fileId + "/download")
                        .header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isPartialContent())
                .andExpect(content().string("cdef"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "4"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"));
    }

    @Test
    @WithMockUser
    void downloadFileUnsatisfiableRangeReturns416() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.openDownload(eq(fileId), any(), eq("bytes=20-30")))
                .thenThrow(new InvalidDownloadRangeException(10L));

        mockMvc.perform(get("/files/" + fileId + "/download")
                        .header(HttpHeaders.RANGE, "bytes=20-30"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"));
    }

    @Test
    @WithMockUser
    void downloadFileMissingStorageObjectReturnsSafe404() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.openDownload(eq(fileId), any(), nullable(String.class)))
                .thenThrow(new StorageObjectNotFoundException("Storage object not found", null));

        mockMvc.perform(get("/files/" + fileId + "/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File content was not found."));
    }

    @Test
    @WithMockUser
    void createPresignedDownloadUrlReturnsShortLivedResponse() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.createPresignedDownloadUrl(eq(fileId), any()))
                .thenReturn(PresignedDownloadUrl.builder()
                        .url("https://storage.example.test/presigned?signature=abc")
                        .method("GET")
                        .expiresAt(Instant.parse("2026-06-07T12:34:56Z"))
                        .expiresInSeconds(300L)
                        .build());

        mockMvc.perform(post("/files/" + fileId + "/download-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage.example.test/presigned?signature=abc"))
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.expiresAt").value("2026-06-07T12:34:56Z"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    private FileDownload download(String name, String mimeType, String content) {
        return download(name, mimeType, content, null);
    }

    private FileDownload download(String name, String mimeType, String content, String etag) {
        byte[] bytes = content.getBytes();

        return FileDownload.builder()
                .name(name)
                .mimeType(mimeType)
                .completeSize(bytes.length)
                .contentLength(bytes.length)
                .etag(etag)
                .content(new ByteArrayInputStream(bytes))
                .build();
    }
}
