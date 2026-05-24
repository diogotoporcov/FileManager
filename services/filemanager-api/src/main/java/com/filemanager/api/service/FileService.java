package com.filemanager.api.service;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.Organization;
import com.filemanager.api.entity.User;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.port.ObjectStoragePort;
import com.filemanager.api.port.StoreObjectRequest;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.OrganizationRepository;
import com.filemanager.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectStoragePort objectStoragePort;

    @Transactional
    public FileEntity uploadFile(String fileName, String contentType, long size, InputStream content, UUID ownerUserId, UUID ownerOrganizationId) {
        if ((ownerUserId != null && ownerOrganizationId != null) || (ownerUserId == null && ownerOrganizationId == null)) {
            throw new IllegalArgumentException("Exactly one owner (user or organization) must be provided");
        }

        User ownerUser = null;
        if (ownerUserId != null) {
            ownerUser = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));
        }

        Organization ownerOrganization = null;
        if (ownerOrganizationId != null) {
            ownerOrganization = organizationRepository.findById(ownerOrganizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + ownerOrganizationId));
        }

        String storagePath = UUID.randomUUID().toString();

        objectStoragePort.putObject(StoreObjectRequest.builder()
                .storagePath(storagePath)
                .content(content)
                .size(size)
                .contentType(contentType)
                .build());

        FileEntity fileEntity = FileEntity.builder()
                .name(fileName)
                .storagePath(storagePath)
                .mimeType(contentType)
                .size(size)
                .ownerUser(ownerUser)
                .ownerOrganization(ownerOrganization)
                .build();

        return fileRepository.save(fileEntity);
    }

    public List<FileEntity> listFiles(UUID ownerUserId, UUID ownerOrganizationId) {
        if (ownerUserId != null) {
            User user = userRepository.findById(ownerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerUserId));
            return fileRepository.findAllByOwnerUserAndDeletedAtIsNull(user);
        } else if (ownerOrganizationId != null) {
            Organization org = organizationRepository.findById(ownerOrganizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + ownerOrganizationId));
            return fileRepository.findAllByOwnerOrganizationAndDeletedAtIsNull(org);
        } else {
            throw new IllegalArgumentException("Either ownerUserId or ownerOrganizationId must be provided");
        }
    }

    public FileEntity getFileMetadata(UUID fileId) {
        return fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
    }

    public InputStream downloadFile(UUID fileId) {
        FileEntity file = getFileMetadata(fileId);
        return objectStoragePort.getObject(file.getStoragePath());
    }

    @Transactional
    public void deleteFile(UUID fileId) {
        FileEntity file = getFileMetadata(fileId);
        file.setDeletedAt(OffsetDateTime.now());
        fileRepository.save(file);
    }
}
