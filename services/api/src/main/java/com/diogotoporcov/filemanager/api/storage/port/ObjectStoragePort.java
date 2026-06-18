package com.diogotoporcov.filemanager.api.storage.port;

public interface ObjectStoragePort {
    StoreObjectResponse putObject(StoreObjectRequest request);
    GetObjectResponse getObject(GetObjectRequest request);
    CreatePresignedDownloadUrlResponse createPresignedDownloadUrl(CreatePresignedDownloadUrlRequest request);
    void deleteObject(String storagePath);
}
