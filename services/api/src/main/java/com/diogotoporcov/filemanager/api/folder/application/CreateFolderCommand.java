package com.diogotoporcov.filemanager.api.folder.application;

import java.util.UUID;

public record CreateFolderCommand(String name, UUID parentFolderId) {
}
