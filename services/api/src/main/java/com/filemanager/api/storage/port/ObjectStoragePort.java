package com.filemanager.api.storage.port;

import java.io.InputStream;

public interface ObjectStoragePort {
    StoreObjectResponse putObject(StoreObjectRequest request);
    InputStream getObject(String storagePath);
    void deleteObject(String storagePath);
}
