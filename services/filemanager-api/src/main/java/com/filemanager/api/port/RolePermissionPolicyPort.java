package com.filemanager.api.port;

import com.filemanager.api.auth.Permission;
import com.filemanager.api.entity.OrganizationMember.MemberRole;

import java.util.Set;

public interface RolePermissionPolicyPort {
    Set<Permission> resolvePermissions(MemberRole role);

    default boolean hasPermission(MemberRole role, Permission permission) {
        return resolvePermissions(role).contains(permission);
    }
}
