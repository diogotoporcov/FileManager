package com.diogotoporcov.filemanager.api.sharing.web;

import com.diogotoporcov.filemanager.api.auth.domain.Permission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFileGrantRequest {
    @NotNull
    private UUID granteeUserId;

    @NotEmpty
    private List<Permission> permissions;
}
