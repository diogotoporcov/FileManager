package com.diogotoporcov.filemanager.api.tag.application;

import com.diogotoporcov.filemanager.api.tag.domain.TagScopeType;
import java.util.UUID;

public record CreateTagCommand(String name, TagScopeType scopeType, UUID scopeFolderId) {
}
