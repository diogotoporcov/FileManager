package com.filemanager.api.dto;

import com.filemanager.api.entity.DuplicateCandidate.CandidateStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateStatusUpdateRequest {
    @NotNull
    private CandidateStatus status;
}
