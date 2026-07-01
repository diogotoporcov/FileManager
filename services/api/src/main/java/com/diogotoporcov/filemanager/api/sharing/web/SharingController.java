package com.diogotoporcov.filemanager.api.sharing.web;

import com.diogotoporcov.filemanager.api.auth.application.CurrentUserProvider;
import com.diogotoporcov.filemanager.api.sharing.application.SharingService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class SharingController {
    private final SharingService sharingService;
    private final CurrentUserProvider currentUserProvider;
    private final GrantResponseMapper grantResponseMapper;

    @PostMapping("/files/{fileId}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public List<GrantResponse> createFileGrants(
            @PathVariable UUID fileId,
            @Valid @RequestBody CreateFileGrantRequest request) {
        UUID actorUserId = currentUserProvider.getCurrentUserId();

        return sharingService.createFileGrants(fileId, request.getGranteeUserId(), request.getPermissions(), actorUserId)
                .stream()
                .map(grantResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/files/{fileId}/grants")
    public List<GrantResponse> listFileGrants(@PathVariable UUID fileId) {
        UUID actorUserId = currentUserProvider.getCurrentUserId();

        return sharingService.listFileGrants(fileId, actorUserId)
                .stream()
                .map(grantResponseMapper::toResponse)
                .toList();
    }

    @DeleteMapping("/files/{fileId}/grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeFileGrant(@PathVariable UUID fileId, @PathVariable UUID grantId) {
        sharingService.revokeFileGrant(fileId, grantId, currentUserProvider.getCurrentUserId());
    }

    @PostMapping("/folders/{folderId}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public List<GrantResponse> createFolderGrants(
            @PathVariable UUID folderId,
            @Valid @RequestBody CreateFolderGrantRequest request) {
        UUID actorUserId = currentUserProvider.getCurrentUserId();

        return sharingService.createFolderGrants(
                        folderId,
                        request.getGranteeUserId(),
                        request.getPermissions(),
                        request.getScope(),
                        actorUserId)
                .stream()
                .map(grantResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/folders/{folderId}/grants")
    public List<GrantResponse> listFolderGrants(@PathVariable UUID folderId) {
        UUID actorUserId = currentUserProvider.getCurrentUserId();

        return sharingService.listFolderGrants(folderId, actorUserId)
                .stream()
                .map(grantResponseMapper::toResponse)
                .toList();
    }

    @DeleteMapping("/folders/{folderId}/grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeFolderGrant(@PathVariable UUID folderId, @PathVariable UUID grantId) {
        sharingService.revokeFolderGrant(folderId, grantId, currentUserProvider.getCurrentUserId());
    }
}
