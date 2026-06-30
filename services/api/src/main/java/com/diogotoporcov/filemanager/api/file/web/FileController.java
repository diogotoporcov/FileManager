package com.diogotoporcov.filemanager.api.file.web;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.application.CursorPage;
import com.diogotoporcov.filemanager.api.file.application.FileDownload;
import com.diogotoporcov.filemanager.api.file.application.FileTransferPolicy;
import com.diogotoporcov.filemanager.api.file.application.FindFilesQuery;
import com.diogotoporcov.filemanager.api.file.application.PresignedDownloadUrl;
import com.diogotoporcov.filemanager.api.file.application.FileService;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.web.CursorPageResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "Endpoints for uploading, listing, and managing files")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Unauthenticated - Invalid or missing JWT", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
})
public class FileController {

    private final FileService fileService;
    private final CurrentUserService currentUserService;
    private final FileResponseMapper fileResponseMapper;

    @Operation(summary = "Upload a file", description = "Uploads a new file owned by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or empty file", content = @Content)
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse uploadFile(
            @Parameter(description = "The file to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Folder ID to upload into.") @RequestParam(value = "folderId", required = false) UUID folderId
    ) throws IOException {
        validateUpload(file);

        UUID actorUserId = currentUserService.getCurrentUserId();
        FileEntity entity = fileService.uploadFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                folderId,
                actorUserId
        );

        return fileResponseMapper.toResponse(entity);
    }

    @Operation(
            summary = "List files",
            description = """
                    Lists files visible to the authenticated user. Filters are applied in the database before sorting and limiting.
                    When folderId is provided, the actor must be able to view the folder and only direct files in that folder are listed.
                    tagId filters files by reusable tag assignment through the database.
                    Dates use ISO-8601 offset date-time values. Repeat mimeType to match any listed exact MIME type.
                    Sort syntax is field,direction with allowed fields createdAt, updatedAt, name, and size. Default is createdAt,desc.
                    size and limit are bounded aliases with default 50 and maximum 200. Invalid search parameters return 400.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of files"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameter", content = @Content)
    })
    @GetMapping
    public CursorPageResponse<FileResponse> listFiles(@Valid @ParameterObject com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery query) {
        UUID actorUserId = currentUserService.getCurrentUserId();

        return toResponsePage(fileService.searchFiles(toApplicationQuery(query), actorUserId));
    }

    @Operation(summary = "Get file metadata", description = "Retrieves metadata for a specific file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File metadata found"),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping("/{fileId}")
    public FileResponse getFileMetadata(@Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        FileEntity entity = fileService.getFileMetadata(fileId, actorUserId);

        return fileResponseMapper.toResponse(entity);
    }

    @Operation(summary = "Download file", description = "Downloads the content of a specific file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File content stream"),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @Parameter(description = "ID of the file") @PathVariable UUID fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        FileDownload download = fileService.openDownload(fileId, actorUserId, rangeHeader);
        StreamingResponseBody body = outputStream -> {
            try (InputStream content = download.getContent()) {
                content.transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity
                .status(download.isPartialContent() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(MediaType.parseMediaType(FileTransferPolicy.safeContentType(download.getMimeType())))
                .contentLength(download.getContentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, FileTransferPolicy.attachmentContentDisposition(download.getName()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(CacheControl.noStore());

        String safeEtag = FileTransferPolicy.safeEtag(download.getEtag());
        if (safeEtag != null) {
            responseBuilder.header(HttpHeaders.ETAG, safeEtag);
        }

        if (download.isPartialContent()) {
            responseBuilder.header(HttpHeaders.CONTENT_RANGE, "bytes "
                    + download.getRangeStart() + "-" + download.getRangeEnd() + "/" + download.getCompleteSize());
        }

        return responseBuilder.body(body);
    }

    @Operation(summary = "Create presigned download URL", description = "Creates a short-lived permission-checked GET URL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Presigned download URL created"),
            @ApiResponse(responseCode = "400", description = "Presigned downloads are disabled", content = @Content),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @PostMapping("/{fileId}/download-url")
    public PresignedDownloadUrlResponse createPresignedDownloadUrl(
            @Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        PresignedDownloadUrl downloadUrl = fileService.createPresignedDownloadUrl(fileId, actorUserId);

        return PresignedDownloadUrlResponse.builder()
                .url(downloadUrl.getUrl())
                .method(downloadUrl.getMethod())
                .expiresAt(downloadUrl.getExpiresAt())
                .expiresInSeconds(downloadUrl.getExpiresInSeconds())
                .build();
    }

    private void validateUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is missing or empty");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("Filename is missing");
        }
    }

    private FindFilesQuery toApplicationQuery(com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery query) {
        return new FindFilesQuery(
                query.getFolderId(),
                query.getTagId(),
                query.getCreatedAtFrom(),
                query.getCreatedAtTo(),
                query.getUpdatedAtFrom(),
                query.getUpdatedAtTo(),
                query.getSizeMin(),
                query.getSizeMax(),
                query.getMimeType(),
                query.getSort(),
                query.getSize(),
                query.getLimit(),
                query.getCursor());
    }

    private CursorPageResponse<FileResponse> toResponsePage(CursorPage<FileEntity> page) {
        return CursorPageResponse.<FileResponse>builder()
                .items(page.items().stream().map(fileResponseMapper::toResponse).toList())
                .nextCursor(page.nextCursor())
                .hasMore(page.hasMore())
                .pageSize(page.pageSize())
                .build();
    }

    @Operation(summary = "Delete file", description = "Deletes a specific file.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "File deleted successfully"),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        fileService.deleteFile(fileId, actorUserId);
    }

}
