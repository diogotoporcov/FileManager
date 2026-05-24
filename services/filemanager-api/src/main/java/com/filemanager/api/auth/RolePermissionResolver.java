package com.filemanager.api.auth;

import com.filemanager.api.entity.OrganizationMember.MemberRole;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.filemanager.api.auth.Permission.*;

@Component
public class RolePermissionResolver {

    // Static mapping defining granular permissions for each organizational role.
    private static final Map<MemberRole, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            MemberRole.VIEWER, EnumSet.of(FILE_VIEW, DUPLICATE_VIEW),
            MemberRole.CONTRIBUTOR, EnumSet.of(FILE_VIEW, FILE_UPLOAD, DUPLICATE_VIEW),
            MemberRole.EDITOR, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, DUPLICATE_VIEW),
            MemberRole.MANAGER, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE, DUPLICATE_VIEW, DUPLICATE_MANAGE),
            MemberRole.ADMIN, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE, DUPLICATE_VIEW, DUPLICATE_MANAGE, ORGANIZATION_MANAGE_MEMBERS, ORGANIZATION_MANAGE_ROLES),
            MemberRole.OWNER, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE, DUPLICATE_VIEW, DUPLICATE_MANAGE, ORGANIZATION_MANAGE_MEMBERS, ORGANIZATION_MANAGE_ROLES)
    );

    public Set<Permission> resolvePermissions(MemberRole role) {
        return ROLE_PERMISSIONS.getOrDefault(role, EnumSet.noneOf(Permission.class));
    }

    public boolean hasPermission(MemberRole role, Permission permission) {
        return resolvePermissions(role).contains(permission);
    }
}
