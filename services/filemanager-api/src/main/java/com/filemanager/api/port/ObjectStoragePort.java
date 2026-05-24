package com.filemanager.api.port;

import java.io.InputStream;

public interface ObjectStoragePort {
    StoredObject putObject(StoreObjectRequest request);
    InputStream getObject(String storagePath);
    void deleteObject(String storagePath);
}
