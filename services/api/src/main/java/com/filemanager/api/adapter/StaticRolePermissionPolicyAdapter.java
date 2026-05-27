package com.filemanager.api.adapter;

import com.filemanager.api.auth.Permission;
import com.filemanager.api.entity.OrganizationMember.MemberRole;
import com.filemanager.api.port.RolePermissionPolicyPort;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.filemanager.api.auth.Permission.DUPLICATE_MANAGE;
import static com.filemanager.api.auth.Permission.DUPLICATE_VIEW;
import static com.filemanager.api.auth.Permission.FILE_DELETE;
import static com.filemanager.api.auth.Permission.FILE_MODIFY;
import static com.filemanager.api.auth.Permission.FILE_SHARE;
import static com.filemanager.api.auth.Permission.FILE_UPLOAD;
import static com.filemanager.api.auth.Permission.FILE_VIEW;
import static com.filemanager.api.auth.Permission.ORGANIZATION_MANAGE_MEMBERS;
import static com.filemanager.api.auth.Permission.ORGANIZATION_MANAGE_ROLES;

@Component
public class StaticRolePermissionPolicyAdapter implements RolePermissionPolicyPort {

    private static final Map<MemberRole, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            MemberRole.VIEWER, EnumSet.of(FILE_VIEW, DUPLICATE_VIEW),
            MemberRole.CONTRIBUTOR, EnumSet.of(FILE_VIEW, FILE_UPLOAD, DUPLICATE_VIEW),
            MemberRole.EDITOR, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, DUPLICATE_VIEW),
            MemberRole.MANAGER, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE, DUPLICATE_VIEW, DUPLICATE_MANAGE),
            MemberRole.ADMIN, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE, DUPLICATE_VIEW, DUPLICATE_MANAGE, ORGANIZATION_MANAGE_MEMBERS, ORGANIZATION_MANAGE_ROLES),
            MemberRole.OWNER, EnumSet.of(FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE, DUPLICATE_VIEW, DUPLICATE_MANAGE, ORGANIZATION_MANAGE_MEMBERS, ORGANIZATION_MANAGE_ROLES)
    );

    @Override
    public Set<Permission> resolvePermissions(MemberRole role) {
        return ROLE_PERMISSIONS.getOrDefault(role, EnumSet.noneOf(Permission.class));
    }
}
