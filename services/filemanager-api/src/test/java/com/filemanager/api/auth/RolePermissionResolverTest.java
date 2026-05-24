package com.filemanager.api.auth;

import com.filemanager.api.entity.OrganizationMember.MemberRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.filemanager.api.auth.Permission.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionResolverTest {

    private final RolePermissionResolver resolver = new RolePermissionResolver();

    @Test
    void viewer_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.VIEWER);
        assertTrue(permissions.contains(FILE_VIEW));
        assertTrue(permissions.contains(DUPLICATE_VIEW));
        assertTrue(!permissions.contains(FILE_UPLOAD));
        assertTrue(!permissions.contains(FILE_DELETE));
        assertTrue(!permissions.contains(DUPLICATE_MANAGE));
    }

    @Test
    void contributor_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.CONTRIBUTOR);
        assertTrue(permissions.contains(FILE_UPLOAD));
        assertTrue(!permissions.contains(FILE_MODIFY));
        assertTrue(!permissions.contains(FILE_DELETE));
    }

    @Test
    void editor_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.EDITOR);
        assertTrue(permissions.contains(FILE_MODIFY));
        assertTrue(!permissions.contains(FILE_DELETE));
        assertTrue(!permissions.contains(DUPLICATE_MANAGE));
    }

    @Test
    void manager_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.MANAGER);
        assertTrue(permissions.contains(FILE_DELETE));
        assertTrue(permissions.contains(FILE_SHARE));
        assertTrue(permissions.contains(DUPLICATE_MANAGE));
        assertTrue(!permissions.contains(ORGANIZATION_MANAGE_MEMBERS));
        assertTrue(!permissions.contains(ORGANIZATION_MANAGE_ROLES));
    }

    @Test
    void admin_ShouldHaveCorrectPermissions() {
        Set<Permission> permissions = resolver.resolvePermissions(MemberRole.ADMIN);
        Set<Permission> expected = Set.of(
                FILE_VIEW, FILE_UPLOAD, FILE_MODIFY, FILE_DELETE, FILE_SHARE,
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
                DUPLICATE_VIEW, DUPLICATE_MANAGE,
                ORGANIZATION_MANAGE_MEMBERS, ORGANIZATION_MANAGE_ROLES
        );
        assertEquals(expected, permissions);
    }
}
