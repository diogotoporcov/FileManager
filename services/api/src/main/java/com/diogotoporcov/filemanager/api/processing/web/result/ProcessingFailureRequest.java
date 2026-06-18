package com.diogotoporcov.filemanager.api.processing.web.result;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingFailureRequest {
    @NotNull
    private UUID fileId;

    @NotBlank
    private String errorMessage;
}
