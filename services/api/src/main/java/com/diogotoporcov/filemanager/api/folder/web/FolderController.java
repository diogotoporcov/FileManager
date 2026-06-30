package com.diogotoporcov.filemanager.api.folder.web;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.application.CursorPage;
import com.diogotoporcov.filemanager.api.file.application.FindFilesQuery;
import com.diogotoporcov.filemanager.api.file.web.FileResponse;
import com.diogotoporcov.filemanager.api.file.web.FileResponseMapper;
import com.diogotoporcov.filemanager.api.file.application.FileService;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.folder.application.CreateFolderCommand;
import com.diogotoporcov.filemanager.api.folder.application.FolderService;
import com.diogotoporcov.filemanager.api.folder.application.RenameFolderCommand;
import com.diogotoporcov.filemanager.api.web.CursorPageResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
@Tag(name = "Folder Management", description = "Endpoints for folder containers and folder-scoped files")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Unauthenticated - Invalid or missing JWT", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
})
public class FolderController {
    private final FolderService folderService;
    private final FileService fileService;
    private final CurrentUserService currentUserService;
    private final FileResponseMapper fileResponseMapper;
    private final FolderResponseMapper folderResponseMapper;

    @Operation(summary = "Create a folder", description = "Creates a root folder owned by the authenticated user or a child folder under an accessible parent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Folder created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content),
            @ApiResponse(responseCode = "409", description = "Duplicate active sibling folder name", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse createFolder(@Valid @RequestBody CreateFolderRequest request) {
        return folderResponseMapper.toResponse(folderService.createFolder(toCommand(request), currentUserService.getCurrentUserId()));
    }

    @Operation(summary = "List root folders", description = "Lists active root folders visible to the authenticated user.")
    @GetMapping
    public FolderChildrenResponse listRootFolders(
            @Parameter(description = "Tag ID to filter root folders by assignment") @RequestParam(required = false) UUID tagId) {
        return FolderChildrenResponse.builder()
                .folders(folderService.listRootFolders(tagId, currentUserService.getCurrentUserId())
                        .stream()
                        .map(folderResponseMapper::toSummary)
                        .toList())
                .build();
    }

    @Operation(summary = "Get folder metadata", description = "Retrieves metadata for an active folder.")
    @GetMapping("/{folderId}")
    public FolderResponse getFolder(@Parameter(description = "ID of the folder") @PathVariable UUID folderId) {
        return folderResponseMapper.toResponse(folderService.getFolder(folderId, currentUserService.getCurrentUserId()));
    }

    @Operation(summary = "Rename folder", description = "Renames an active folder.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Folder renamed successfully"),
            @ApiResponse(responseCode = "409", description = "Duplicate active sibling folder name", content = @Content)
    })
    @PatchMapping("/{folderId}")
    public FolderResponse renameFolder(
            @Parameter(description = "ID of the folder") @PathVariable UUID folderId,
            @Valid @RequestBody UpdateFolderRequest request) {
        return folderResponseMapper.toResponse(folderService.renameFolder(
                folderId,
                new RenameFolderCommand(request.getName()),
                currentUserService.getCurrentUserId()));
    }

    @Operation(
            summary = "Delete folder",
            description = "Soft deletes an empty folder. Non-empty folders are rejected with 409.")
    @DeleteMapping("/{folderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@Parameter(description = "ID of the folder") @PathVariable UUID folderId) {
        folderService.deleteFolder(folderId, currentUserService.getCurrentUserId());
    }

    @Operation(summary = "List child folders", description = "Lists direct child folders only.")
    @GetMapping("/{folderId}/children")
    public FolderChildrenResponse listChildFolders(
            @Parameter(description = "ID of the folder") @PathVariable UUID folderId,
            @Parameter(description = "Tag ID to filter child folders by assignment") @RequestParam(required = false) UUID tagId) {
        return FolderChildrenResponse.builder()
                .folders(folderService.listChildFolders(folderId, tagId, currentUserService.getCurrentUserId())
                        .stream()
                        .map(folderResponseMapper::toSummary)
                        .toList())
                .build();
    }

    @Operation(summary = "Create child folder", description = "Creates a direct child folder under the parent folder.")
    @PostMapping("/{folderId}/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse createChildFolder(
            @Parameter(description = "ID of the parent folder") @PathVariable UUID folderId,
            @Valid @RequestBody CreateFolderRequest request) {
        return folderResponseMapper.toResponse(folderService.createFolder(
                new CreateFolderCommand(request.getName(), folderId),
                currentUserService.getCurrentUserId()));
    }

    @Operation(
            summary = "List files in folder",
            description = "Lists files directly inside the folder. Existing filters, sorting, and cursor pagination apply.")
    @GetMapping("/{folderId}/files")
    public CursorPageResponse<FileResponse> listFolderFiles(
            @Parameter(description = "ID of the folder") @PathVariable UUID folderId,
            @Valid @ParameterObject com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery query) {

        return toResponsePage(fileService.searchFiles(toApplicationQuery(query, folderId), currentUserService.getCurrentUserId()));
    }

    @Operation(summary = "Upload file into folder", description = "Uploads a file owned by the authenticated user into the folder.")
    @PostMapping(value = "/{folderId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse uploadFileIntoFolder(
            @Parameter(description = "ID of the folder") @PathVariable UUID folderId,
            @Parameter(description = "The file to upload") @RequestParam("file") MultipartFile file) throws IOException {
        validateUpload(file);

        UUID actorUserId = currentUserService.getCurrentUserId();
        FileEntity entity = fileService.uploadFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                folderId,
                actorUserId);

        return fileResponseMapper.toResponse(entity);
    }

    private void validateUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is missing or empty");
        }

        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("Filename is missing");
        }
    }

    private CreateFolderCommand toCommand(CreateFolderRequest request) {
        return new CreateFolderCommand(request.getName(), request.getParentFolderId());
    }

    private FindFilesQuery toApplicationQuery(
            com.diogotoporcov.filemanager.api.file.web.search.FileSearchQuery query,
            UUID folderId) {
        return new FindFilesQuery(
                folderId,
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
}
