package com.filemanager.api.controller;

import com.filemanager.api.auth.CurrentUserService;
import com.filemanager.api.dto.FileResponse;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final CurrentUserService currentUserService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "ownerUserId", required = false) UUID ownerUserId,
            @RequestParam(value = "ownerOrganizationId", required = false) UUID ownerOrganizationId
    ) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is missing or empty");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("Filename is missing");
        }

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

    @GetMapping
    public List<FileResponse> listFiles(
            @RequestParam(value = "ownerUserId", required = false) UUID ownerUserId,
            @RequestParam(value = "ownerOrganizationId", required = false) UUID ownerOrganizationId
    ) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        return fileService.listFiles(ownerUserId, ownerOrganizationId, actorUserId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{fileId}")
    public FileResponse getFileMetadata(@PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        FileEntity entity = fileService.getFileMetadata(fileId, actorUserId);
        return mapToResponse(entity);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID fileId) {
        UUID actorUserId = currentUserService.getCurrentUserId();
        FileEntity entity = fileService.getFileMetadata(fileId, actorUserId);
        Resource resource = new InputStreamResource(fileService.downloadFile(fileId, actorUserId));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(entity.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable UUID fileId) {
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
