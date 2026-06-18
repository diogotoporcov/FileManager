package com.diogotoporcov.filemanager.api.sharing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.diogotoporcov.filemanager.api.auth.application.CurrentUserService;
import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import com.diogotoporcov.filemanager.api.exception.AccessDeniedException;
import com.diogotoporcov.filemanager.api.exception.GlobalExceptionHandler;
import com.diogotoporcov.filemanager.api.exception.ResourceNotFoundException;
import com.diogotoporcov.filemanager.api.file.domain.FileEntity;
import com.diogotoporcov.filemanager.api.folder.domain.FolderEntity;
import com.diogotoporcov.filemanager.api.identity.domain.User;
import com.diogotoporcov.filemanager.api.sharing.application.SharingService;
import com.diogotoporcov.filemanager.api.sharing.domain.FileGrantEntity;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantEntity;
import com.diogotoporcov.filemanager.api.sharing.domain.FolderGrantScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SharingControllerTest {
    private SharingService sharingService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        sharingService = mock(SharingService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SharingController(
                        sharingService,
                        currentUserService,
                        new GrantResponseMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        actorId = UUID.randomUUID();
        when(currentUserService.getCurrentUserId()).thenReturn(actorId);
    }

    @Test
    void postFolderGrantReturnsCreatedGrantRows() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        FolderGrantEntity grant = FolderGrantEntity.builder()
                .id(UUID.randomUUID())
                .folder(FolderEntity.builder().id(folderId).build())
                .granteeUser(user(granteeId))
                .createdByUser(user(actorId))
                .permission(Permission.FOLDER_VIEW)
                .build();
        when(sharingService.createFolderGrants(
                eq(folderId),
                eq(granteeId),
                any(),
                eq(FolderGrantScope.DIRECT),
                eq(actorId))).thenReturn(List.of(grant));

        mockMvc.perform(post("/folders/{folderId}/grants", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", granteeId,
                                "permissions", List.of("FOLDER_VIEW")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].resourceType").value("FOLDER"))
                .andExpect(jsonPath("$[0].permission").value("FOLDER_VIEW"))
                .andExpect(jsonPath("$[0].scope").value("DIRECT"));
    }

    @Test
    void postFolderGrantAcceptsRecursiveScope() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        FolderGrantEntity grant = FolderGrantEntity.builder()
                .id(UUID.randomUUID())
                .folder(FolderEntity.builder().id(folderId).build())
                .granteeUser(user(granteeId))
                .createdByUser(user(actorId))
                .permission(Permission.FOLDER_VIEW)
                .scope(FolderGrantScope.RECURSIVE)
                .build();
        when(sharingService.createFolderGrants(
                eq(folderId),
                eq(granteeId),
                any(),
                eq(FolderGrantScope.RECURSIVE),
                eq(actorId))).thenReturn(List.of(grant));

        mockMvc.perform(post("/folders/{folderId}/grants", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", granteeId,
                                "permissions", List.of("FOLDER_VIEW"),
                                "scope", "RECURSIVE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].scope").value("RECURSIVE"));
    }

    @Test
    void postFolderGrantRejectsInvalidScope() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();

        mockMvc.perform(post("/folders/{folderId}/grants", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "granteeUserId": "%s",
                                  "permissions": ["FOLDER_VIEW"],
                                  "scope": "INVALID"
                                }
                                """.formatted(granteeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request body"));
    }

    @Test
    void getFolderGrantsReturnsRows() throws Exception {
        UUID folderId = UUID.randomUUID();
        FolderGrantEntity grant = FolderGrantEntity.builder()
                .id(UUID.randomUUID())
                .folder(FolderEntity.builder().id(folderId).build())
                .granteeUser(user(UUID.randomUUID()))
                .createdByUser(user(actorId))
                .permission(Permission.FOLDER_UPLOAD_FILE)
                .build();
        when(sharingService.listFolderGrants(folderId, actorId)).thenReturn(List.of(grant));

        mockMvc.perform(get("/folders/{folderId}/grants", folderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].permission").value("FOLDER_UPLOAD_FILE"))
                .andExpect(jsonPath("$[0].scope").value("DIRECT"));
    }

    @Test
    void deleteFolderGrantReturnsNoContent() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();

        mockMvc.perform(delete("/folders/{folderId}/grants/{grantId}", folderId, grantId))
                .andExpect(status().isNoContent());

        verify(sharingService).revokeFolderGrant(folderId, grantId, actorId);
    }

    @Test
    void postFileGrantReturnsCreatedGrantRows() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        FileGrantEntity grant = FileGrantEntity.builder()
                .id(UUID.randomUUID())
                .file(FileEntity.builder().id(fileId).build())
                .granteeUser(user(granteeId))
                .createdByUser(user(actorId))
                .permission(Permission.FILE_VIEW)
                .build();
        when(sharingService.createFileGrants(eq(fileId), eq(granteeId), any(), eq(actorId))).thenReturn(List.of(grant));

        mockMvc.perform(post("/files/{fileId}/grants", fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", granteeId,
                                "permissions", List.of("FILE_VIEW")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].resourceType").value("FILE"))
                .andExpect(jsonPath("$[0].permission").value("FILE_VIEW"));
    }

    @Test
    void getFileGrantsReturnsRows() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileGrantEntity grant = FileGrantEntity.builder()
                .id(UUID.randomUUID())
                .file(FileEntity.builder().id(fileId).build())
                .granteeUser(user(UUID.randomUUID()))
                .createdByUser(user(actorId))
                .permission(Permission.FILE_DELETE)
                .build();
        when(sharingService.listFileGrants(fileId, actorId)).thenReturn(List.of(grant));

        mockMvc.perform(get("/files/{fileId}/grants", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].permission").value("FILE_DELETE"));
    }

    @Test
    void deleteFileGrantReturnsNoContent() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();

        mockMvc.perform(delete("/files/{fileId}/grants/{grantId}", fileId, grantId))
                .andExpect(status().isNoContent());

        verify(sharingService).revokeFileGrant(fileId, grantId, actorId);
    }

    @Test
    void nonOwnerReceivesForbidden() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        when(sharingService.createFileGrants(eq(fileId), eq(granteeId), any(), eq(actorId)))
                .thenThrow(new AccessDeniedException("Only the file owner can manage file grants."));

        mockMvc.perform(post("/files/{fileId}/grants", fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", granteeId,
                                "permissions", List.of("FILE_VIEW")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void fileSharePermissionReceivesBadRequest() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        when(sharingService.createFileGrants(eq(fileId), eq(granteeId), any(), eq(actorId)))
                .thenThrow(new IllegalArgumentException("Permission FILE_SHARE is not valid for file grants."));

        mockMvc.perform(post("/files/{fileId}/grants", fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", granteeId,
                                "permissions", List.of("FILE_SHARE")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void folderManagePermissionReceivesBadRequest() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        when(sharingService.createFolderGrants(
                eq(folderId),
                eq(granteeId),
                any(),
                eq(FolderGrantScope.DIRECT),
                eq(actorId)))
                .thenThrow(new IllegalArgumentException("Permission FOLDER_MANAGE_PERMISSIONS is not valid for folder grants."));

        mockMvc.perform(post("/folders/{folderId}/grants", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", granteeId,
                                "permissions", List.of("FOLDER_MANAGE_PERMISSIONS")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void selfGrantReceivesBadRequest() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(sharingService.createFolderGrants(
                eq(folderId),
                eq(actorId),
                any(),
                eq(FolderGrantScope.DIRECT),
                eq(actorId)))
                .thenThrow(new IllegalArgumentException("Resource owners already have access and cannot grant resources to themselves."));

        mockMvc.perform(post("/folders/{folderId}/grants", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", actorId,
                                "permissions", List.of("FOLDER_VIEW")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingGranteeReceivesNotFound() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID granteeId = UUID.randomUUID();
        when(sharingService.createFolderGrants(
                eq(folderId),
                eq(granteeId),
                any(),
                eq(FolderGrantScope.DIRECT),
                eq(actorId)))
                .thenThrow(new ResourceNotFoundException("User not found: " + granteeId));

        mockMvc.perform(post("/folders/{folderId}/grants", folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "granteeUserId", granteeId,
                                "permissions", List.of("FOLDER_VIEW")))))
                .andExpect(status().isNotFound());
    }

    private User user(UUID id) {
        return User.builder().id(id).email(id + "@example.com").build();
    }
}
