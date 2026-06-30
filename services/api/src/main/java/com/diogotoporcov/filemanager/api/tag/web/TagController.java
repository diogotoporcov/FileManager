package com.diogotoporcov.filemanager.api.tag.web;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.tag.application.CreateTagCommand;
import com.diogotoporcov.filemanager.api.tag.application.TagService;
import com.diogotoporcov.filemanager.api.tag.domain.TagEntity;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Reusable classifications for files and folders")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Unauthenticated - Invalid or missing JWT", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions", content = @Content),
        @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
})
public class TagController {
    private final TagService tagService;
    private final CurrentUserService currentUserService;
    private final TagResponseMapper tagResponseMapper;

    @Operation(
            summary = "Create or get a tag",
            description = """
                    Creates a reusable classification tag, or returns the existing active tag with the same normalized name
                    inside the selected OWNER or FOLDER scope. Names are trimmed, repeated spaces are collapsed, and
                    case-insensitive uniqueness is enforced by normalized name inside scope.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing or newly created tag"),
            @ApiResponse(responseCode = "400", description = "Invalid tag scope or name", content = @Content)
    })
    @PostMapping("/tags")
    public TagResponse createOrGetTag(@Valid @RequestBody CreateTagRequest request) {
        return tagResponseMapper.toResponse(tagService.createOrGetTag(toCommand(request), currentUserService.getCurrentUserId()));
    }

    @Operation(
            summary = "List tags",
            description = """
                    Lists active reusable classification tags in an owner scope or a folder scope. q performs a
                    normalized contains match for autocomplete. Results are bounded and sorted by normalizedName then id.
                    """)
    @GetMapping("/tags")
    public List<TagResponse> listTags(
            @Parameter(description = "Folder ID for FOLDER-scoped tags") @RequestParam(required = false) UUID scopeFolderId,
            @Parameter(description = "Normalized contains search text") @RequestParam(required = false) String q,
            @Parameter(description = "Maximum tags to return. Default 50, maximum 100.") @RequestParam(required = false) Integer limit) {
        return toResponseList(tagService.listTags(
                scopeFolderId,
                q,
                limit,
                currentUserService.getCurrentUserId()));
    }

    @Operation(summary = "List file tags", description = "Lists active tags assigned to a file.")
    @GetMapping("/files/{fileId}/tags")
    public List<TagResponse> listFileTags(@Parameter(description = "ID of the file") @PathVariable UUID fileId) {
        return toResponseList(tagService.listFileTags(fileId, currentUserService.getCurrentUserId()));
    }

    @Operation(
            summary = "Apply tag to file",
            description = "Applies an existing tag to a file idempotently and returns the file's current tag list.")
    @PostMapping("/files/{fileId}/tags")
    public List<TagResponse> applyTagToFile(
            @Parameter(description = "ID of the file") @PathVariable UUID fileId,
            @Valid @RequestBody ApplyTagRequest request) {
        return toResponseList(tagService.applyTagToFile(fileId, request.getTagId(), currentUserService.getCurrentUserId()));
    }

    @Operation(
            summary = "Remove tag from file",
            description = "Removes the assignment from the file without deleting the reusable tag definition.")
    @DeleteMapping("/files/{fileId}/tags/{tagId}")
    @ResponseStatus(HttpStatus.OK)
    public List<TagResponse> removeTagFromFile(
            @Parameter(description = "ID of the file") @PathVariable UUID fileId,
            @Parameter(description = "ID of the tag") @PathVariable UUID tagId) {
        return toResponseList(tagService.removeTagFromFile(fileId, tagId, currentUserService.getCurrentUserId()));
    }

    @Operation(summary = "List folder tags", description = "Lists active tags assigned to a folder.")
    @GetMapping("/folders/{folderId}/tags")
    public List<TagResponse> listFolderTags(@Parameter(description = "ID of the folder") @PathVariable UUID folderId) {
        return toResponseList(tagService.listFolderTags(folderId, currentUserService.getCurrentUserId()));
    }

    @Operation(
            summary = "Apply tag to folder",
            description = "Applies an existing tag to a folder idempotently and returns the folder's current tag list.")
    @PostMapping("/folders/{folderId}/tags")
    public List<TagResponse> applyTagToFolder(
            @Parameter(description = "ID of the folder") @PathVariable UUID folderId,
            @Valid @RequestBody ApplyTagRequest request) {
        return toResponseList(tagService.applyTagToFolder(folderId, request.getTagId(), currentUserService.getCurrentUserId()));
    }

    @Operation(
            summary = "Remove tag from folder",
            description = "Removes the assignment from the folder without deleting the reusable tag definition.")
    @DeleteMapping("/folders/{folderId}/tags/{tagId}")
    @ResponseStatus(HttpStatus.OK)
    public List<TagResponse> removeTagFromFolder(
            @Parameter(description = "ID of the folder") @PathVariable UUID folderId,
            @Parameter(description = "ID of the tag") @PathVariable UUID tagId) {
        return toResponseList(tagService.removeTagFromFolder(folderId, tagId, currentUserService.getCurrentUserId()));
    }

    private CreateTagCommand toCommand(CreateTagRequest request) {
        return new CreateTagCommand(request.getName(), request.getScopeType(), request.getScopeFolderId());
    }

    private List<TagResponse> toResponseList(List<TagEntity> tags) {
        return tags.stream().map(tagResponseMapper::toResponse).toList();
    }
}
