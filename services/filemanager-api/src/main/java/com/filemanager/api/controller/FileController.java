package com.filemanager.api.controller;

import com.filemanager.api.auth.CurrentUserService;
import com.filemanager.api.dto.FileResponse;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Operation(summary = "Upload a file", description = "Uploads a new file to the storage. Actor is derived from JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or empty file", content = @Content)
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse uploadFile(
            @Parameter(description = "The file to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Owner user ID. For user-owned uploads, this must match the authenticated user. Exactly one ownership context should be provided.") @RequestParam(value = "ownerUserId", required = false) UUID ownerUserId,
            @Parameter(description = "Owner organization ID. For organization-owned uploads, authenticated user must have upload permission. Exactly one ownership context should be provided.") @RequestParam(value = "ownerOrganizationId", required = false) UUID ownerOrganizationId
    ) throws IOException {
        validateUpload(file);

        UUID actorUserId = currentUserService.getCurrentUserId();
        FileEntity entity = fileService.uploadFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                ownerUserId,
                ownerOrganizationId,
                actorUserId
        );

        return mapToResponse(entity);
    }

    @Operation(summary = "List files", description = "Lists files based on ownership/organization filters. Actor is derived from JWT.")
    @ApiResponse(responseCode = "200", description = "List of files")
    @GetMapping
    public List<FileResponse> listFiles(
            @Parameter(description = "Filter by owner User ID") @RequestParam(value = "ownerUserId", required = false) UUID ownerUserId,
            @Parameter(description = "Filter by owner Organization ID") @RequestParam(value = "ownerOrganizationId", required = false) UUID ownerOrganizationId
    ) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return fileService.listFiles(ownerUserId, ownerOrganizationId, actorUserId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
        return mapToResponse(entity);
    }

    @Operation(summary = "Download file", description = "Downloads the content of a specific file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File content stream"),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping(value = "/{fileId}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> downloadFile(@Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        FileEntity entity = fileService.getFileMetadata(fileId, actorUserId);
        Resource resource = new InputStreamResource(fileService.downloadFile(fileId, actorUserId));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(entity.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(safeDownloadFilename(entity.getName()), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    private void validateUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is missing or empty");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("Filename is missing");
        }
    }

    private String safeDownloadFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "download";
        }

        if (filename.contains("\r") || filename.contains("\n")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        return filename;
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

    private FileResponse mapToResponse(FileEntity entity) {
        return FileResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .mimeType(entity.getMimeType())
                .size(entity.getSize())
                .ownerUserId(entity.getOwnerUser() != null ? entity.getOwnerUser().getId() : null)
                .ownerOrganizationId(entity.getOwnerOrganization() != null ? entity.getOwnerOrganization().getId() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
