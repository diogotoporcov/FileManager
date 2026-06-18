package com.diogotoporcov.filemanager.api.processing.web.result;

import jakarta.validation.constraints.NotBlank;
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
public class PhashResultRequest {
    @NotNull
    private UUID fileId;

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{16}$", message = "phash must be a 16-character hex string")
    private String phash;
}
