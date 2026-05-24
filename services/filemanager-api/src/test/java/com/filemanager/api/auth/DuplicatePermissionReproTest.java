package com.filemanager.api.auth;

import com.filemanager.api.entity.*;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicatePermissionReproTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private RolePermissionResolver rolePermissionResolver;

    @InjectMocks
    private AccessControlService accessControlService;

    private UUID actorUserId;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
    }

    @Test
    void assertCanManageDuplicate_OrgViewer_ShouldThrowAccessDenied() {
        UUID candidateId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(organizationId);
        
        FileEntity sourceFile = FileEntity.builder().ownerOrganization(org).build();
        FileEntity candidateFile = FileEntity.builder().ownerOrganization(org).build();
        DuplicateCandidate dc = DuplicateCandidate.builder()
                .sourceFile(sourceFile)
                .candidateFile(candidateFile)
                .build();

        when(duplicateCandidateRepository.findById(candidateId)).thenReturn(Optional.of(dc));
        
        OrganizationMember member = new OrganizationMember();
        member.setRole(OrganizationMember.MemberRole.VIEWER);
        
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, actorUserId))
                .thenReturn(Optional.of(member));

        when(rolePermissionResolver.hasPermission(OrganizationMember.MemberRole.VIEWER, Permission.DUPLICATE_MANAGE))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> 
                accessControlService.assertCanManageDuplicate(actorUserId, candidateId));
    }
}
