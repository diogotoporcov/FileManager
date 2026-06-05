package com.filemanager.api.folder.web;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@Schema(description = "Direct child folders of a folder")
public class FolderChildrenResponse {
    @Schema(description = "Direct child folders")
    List<FolderSummaryResponse> folders;
}
