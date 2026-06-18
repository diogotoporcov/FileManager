package com.diogotoporcov.filemanager.api.storage.adapter;

import com.diogotoporcov.filemanager.api.storage.config.MinioProperties;
import com.diogotoporcov.filemanager.api.storage.exception.StorageObjectNotFoundException;
import com.diogotoporcov.filemanager.api.storage.port.CreatePresignedDownloadUrlRequest;
import com.diogotoporcov.filemanager.api.storage.port.GetObjectRequest;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioAdapterTest {
    @Mock
    private MinioClient minioClient;

    private MinioAdapter adapter;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setBucketName("files");
        adapter = new MinioAdapter(minioClient, properties);
    }

    @Test
    void getObjectFullStreamUsesBucketAndObject() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(minioResponse("data"));

        var response = adapter.getObject(GetObjectRequest.builder()
                .storagePath("object-key")
                .build());

        assertThat(response.getContent().readAllBytes()).isEqualTo("data".getBytes());
        assertThat(response.getContentLength()).isEqualTo(4L);
        assertThat(response.getContentType()).isEqualTo("text/plain");
        assertThat(response.getEtag()).isEqualTo("etag");
        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        org.mockito.Mockito.verify(minioClient).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("files");
        assertThat(captor.getValue().object()).isEqualTo("object-key");
        assertThat(captor.getValue().offset()).isNull();
        assertThat(captor.getValue().length()).isNull();
    }

    @Test
    void getObjectRangedStreamUsesOffsetAndLength() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(minioResponse("partial"));

        adapter.getObject(GetObjectRequest.builder()
                .storagePath("object-key")
                .rangeStart(10L)
                .rangeEnd(19L)
                .build());

        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        org.mockito.Mockito.verify(minioClient).getObject(captor.capture());
        assertThat(captor.getValue().offset()).isEqualTo(10L);
        assertThat(captor.getValue().length()).isEqualTo(10L);
    }

    @Test
    void createPresignedDownloadUrlAddsResponseHeaderOverrides() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://storage.example.test/presigned");

        var response = adapter.createPresignedDownloadUrl(CreatePresignedDownloadUrlRequest.builder()
                .storagePath("object-key")
                .ttl(Duration.ofMinutes(5))
                .responseContentDisposition("attachment; filename=\"test.txt\"")
                .responseContentType("text/plain")
                .build());

        assertThat(response.getUrl()).isEqualTo("https://storage.example.test/presigned");
        assertThat(response.getExpiresAt()).isNotNull();
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        org.mockito.Mockito.verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("files");
        assertThat(captor.getValue().object()).isEqualTo("object-key");
        assertThat(captor.getValue().expiry()).isEqualTo(300);
        assertThat(captor.getValue().extraQueryParams().get("response-content-disposition"))
                .containsExactly("attachment; filename=\"test.txt\"");
        assertThat(captor.getValue().extraQueryParams().get("response-content-type")).containsExactly("text/plain");
    }

    @Test
    void getObjectMapsMissingObjectToStorageObjectNotFound() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(missingObjectException());

        assertThrows(StorageObjectNotFoundException.class, () -> adapter.getObject(GetObjectRequest.builder()
                .storagePath("missing-key")
                .build()));
    }

    private io.minio.GetObjectResponse minioResponse(String content) {
        Headers headers = new Headers.Builder()
                .add("Content-Length", Integer.toString(content.length()))
                .add("Content-Type", "text/plain")
                .add("ETag", "etag")
                .build();

        return new io.minio.GetObjectResponse(
                headers,
                "files",
                null,
                "object-key",
                new ByteArrayInputStream(content.getBytes()));
    }

    private ErrorResponseException missingObjectException() {
        ErrorResponse errorResponse = new ErrorResponse(
                "NoSuchKey",
                "not found",
                "files",
                "missing-key",
                "/files/missing-key",
                "request-id",
                "host-id");
        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://localhost:9000/files/missing-key").build())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .build();

        return new ErrorResponseException(errorResponse, response, "GET /files/missing-key");
    }
}
