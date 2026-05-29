package com.filemanager.api.dto;

import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update the status of a duplicate candidate")
public class DuplicateStatusUpdateRequest {
    @NotNull
    @Schema(description = "New status for the duplicate candidate", example = "CONFIRMED")
    private CandidateStatus status;
}
