package com.filemanager.api.auth;

import com.filemanager.api.entity.OrganizationMember.MemberRole;
import com.filemanager.api.adapter.StaticRolePermissionPolicyAdapter;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.filemanager.api.auth.Permission.*;
import static org.junit.jupiter.api.Assertions.*;

class RolePermissionResolverTest {

    private final StaticRolePermissionPolicyAdapter resolver = new StaticRolePermissionPolicyAdapter();

    @Test
    void viewer_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.VIEWER);
        assertTrue(permissions.contains(FILE_VIEW));
        assertTrue(permissions.contains(FOLDER_VIEW));
        assertTrue(permissions.contains(DUPLICATE_VIEW));
        assertFalse(permissions.contains(FILE_UPLOAD));
        assertFalse(permissions.contains(FOLDER_UPLOAD_FILE));
        assertFalse(permissions.contains(FILE_DELETE));
        assertFalse(permissions.contains(DUPLICATE_MANAGE));
    }

    @Test
    void contributor_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.CONTRIBUTOR);
        assertTrue(permissions.contains(FILE_UPLOAD));
        assertTrue(permissions.contains(FOLDER_CREATE));
        assertTrue(permissions.contains(FOLDER_UPLOAD_FILE));
        assertFalse(permissions.contains(FILE_MODIFY));
        assertFalse(permissions.contains(FILE_DELETE));
    }

    @Test
    void editor_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.EDITOR);
        assertTrue(permissions.contains(FILE_MODIFY));
        assertTrue(permissions.contains(FOLDER_RENAME));
        assertFalse(permissions.contains(FILE_DELETE));
        assertFalse(permissions.contains(FOLDER_DELETE));
        assertFalse(permissions.contains(DUPLICATE_MANAGE));
    }

    @Test
    void manager_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.MANAGER);
        assertTrue(permissions.contains(FILE_DELETE));
        assertTrue(permissions.contains(FILE_SHARE));
        assertTrue(permissions.contains(FOLDER_DELETE));
        assertTrue(permissions.contains(FOLDER_MANAGE_PERMISSIONS));
        assertTrue(permissions.contains(DUPLICATE_MANAGE));
        assertFalse(permissions.contains(ORGANIZATION_MANAGE_MEMBERS));
        assertFalse(permissions.contains(ORGANIZATION_MANAGE_ROLES));
    }

    @Test
    void admin_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.ADMIN);
        Set<Permission> expected = Set.of(
                FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE,
                FOLDER_VIEW, FOLDER_CREATE, FOLDER_RENAME, FOLDER_DELETE,
                FOLDER_UPLOAD_FILE, FOLDER_MANAGE_PERMISSIONS,
                DUPLICATE_VIEW, DUPLICATE_MANAGE,
                ORGANIZATION_MANAGE_MEMBERS, ORGANIZATION_MANAGE_ROLES
        );
        assertEquals(expected, permissions);
    }

    @Test
    void owner_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.OWNER);
        Set<Permission> expected = Set.of(
                FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE,
                FOLDER_VIEW, FOLDER_CREATE, FOLDER_RENAME, FOLDER_DELETE,
                FOLDER_UPLOAD_FILE, FOLDER_MANAGE_PERMISSIONS,
                DUPLICATE_VIEW, DUPLICATE_MANAGE,
                ORGANIZATION_MANAGE_MEMBERS, ORGANIZATION_MANAGE_ROLES
        );
        assertEquals(expected, permissions);
    }
}
