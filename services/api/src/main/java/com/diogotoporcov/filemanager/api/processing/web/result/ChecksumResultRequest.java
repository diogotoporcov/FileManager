package com.diogotoporcov.filemanager.api.processing.web.result;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecksumResultRequest {
    @NotNull
    private UUID fileId;

    @NotNull
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "SHA-256 must be 64 hex characters")
    private String sha256;
}
