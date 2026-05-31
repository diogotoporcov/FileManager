package com.filemanager.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@Schema(description = "On-demand duplicate group for an ownership context")
public class DuplicateGroupResponse {
    @Schema(description = "Strongest method represented in the group")
    DuplicateSearchMethod method;
    @Schema(description = "Original file for this group")
    FileSummaryResponse originalFile;
    @Schema(description = "Duplicate files in this group")
    List<DuplicateMatchResponse> duplicates;
    @Schema(description = "Total number of files in the group, including the original")
    int groupSize;
}
