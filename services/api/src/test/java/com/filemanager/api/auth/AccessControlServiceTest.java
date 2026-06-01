package com.filemanager.api.auth;

import com.filemanager.api.entity.*;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.port.RolePermissionPolicyPort;
import com.filemanager.api.repository.DuplicateCandidateRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.FolderRepository;
import com.filemanager.api.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private DuplicateCandidateRepository duplicateCandidateRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private RolePermissionPolicyPort rolePermissionPolicyPort;

    @InjectMocks
    private AccessControlService accessControlService;

    private UUID actorUserId;
    private UUID fileId;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        fileId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
    }

    @Test
    void assertCanAccessFile_OwnerUser_ExplicitPermissions_Success() {
        User owner = new User();
        owner.setId(actorUserId);
        FileEntity file = FileEntity.builder().ownerUser(owner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_DELETE));
        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.DUPLICATE_VIEW));
        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.DUPLICATE_MANAGE));
    }

    @Test
    void assertCanAccessFile_NotOwnerUser_Forbidden() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        FileEntity file = FileEntity.builder().ownerUser(owner).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        assertThrows(AccessDeniedException.class, () -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
    }

    @Test
    void assertCanAccessFile_Deleted_NotFound() {
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
    }

    @Test
    void assertCanAccessFile_OrganizationMember_Success() {
        Organization org = new Organization();
        org.setId(organizationId);
        FileEntity file = FileEntity.builder().ownerOrganization(org).build();
        when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

        OrganizationMember member = new OrganizationMember();
        member.setRole(OrganizationMember.MemberRole.VIEWER);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, actorUserId)).thenReturn(Optional.of(member));
        when(rolePermissionPolicyPort.hasPermission(OrganizationMember.MemberRole.VIEWER, Permission.FILE_VIEW)).thenReturn(true);

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.FILE_VIEW));
    }

    @Test
    void assertCanAccessFolder_OwnerUser_Success() {
        UUID folderId = UUID.randomUUID();
        User owner = new User();
        owner.setId(actorUserId);
        FolderEntity folder = FolderEntity.builder().ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFolder(
                actorUserId,
                folderId,
                Permission.FOLDER_VIEW));
    }

    @Test
    void assertCanAccessFolder_NotOwnerUser_Forbidden() {
        UUID folderId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        FolderEntity folder = FolderEntity.builder().ownerUser(owner).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        assertThrows(AccessDeniedException.class, () -> accessControlService.assertCanAccessFolder(
                actorUserId,
                folderId,
                Permission.FOLDER_VIEW));
    }

    @Test
    void assertCanAccessFolder_OrganizationMemberRequiresPermission() {
        UUID folderId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(organizationId);
        FolderEntity folder = FolderEntity.builder().ownerOrganization(org).build();
        when(folderRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));

        OrganizationMember member = new OrganizationMember();
        member.setRole(OrganizationMember.MemberRole.CONTRIBUTOR);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, actorUserId))
                .thenReturn(Optional.of(member));
        when(rolePermissionPolicyPort.hasPermission(
                OrganizationMember.MemberRole.CONTRIBUTOR,
                Permission.FOLDER_UPLOAD_FILE)).thenReturn(true);

        assertDoesNotThrow(() -> accessControlService.assertCanAccessFolder(
                actorUserId,
                folderId,
                Permission.FOLDER_UPLOAD_FILE));
    }

    @Test
    void assertCanManageDuplicate_Bidirectional_SourceAllowed_Success() {
        UUID candidateId = UUID.randomUUID();
        User owner = new User();
        owner.setId(actorUserId);
        FileEntity sourceFile = FileEntity.builder().ownerUser(owner).build();
        FileEntity candidateFile = FileEntity.builder().ownerUser(new User()).build();
        DuplicateCandidate dc = DuplicateCandidate.builder()
                .sourceFile(sourceFile)
                .candidateFile(candidateFile)
                .build();

        when(duplicateCandidateRepository.findById(candidateId)).thenReturn(Optional.of(dc));

        assertDoesNotThrow(() -> accessControlService.assertCanManageDuplicate(actorUserId, candidateId));
    }

    @Test
    void assertCanManageDuplicate_Bidirectional_CandidateAllowed_Success() {
        UUID candidateId = UUID.randomUUID();
        User owner = new User();
        owner.setId(actorUserId);
        FileEntity sourceFile = FileEntity.builder().ownerUser(new User()).build();
        FileEntity candidateFile = FileEntity.builder().ownerUser(owner).build();
        DuplicateCandidate dc = DuplicateCandidate.builder()
                .sourceFile(sourceFile)
                .candidateFile(candidateFile)
                .build();

        when(duplicateCandidateRepository.findById(candidateId)).thenReturn(Optional.of(dc));

        assertDoesNotThrow(() -> accessControlService.assertCanManageDuplicate(actorUserId, candidateId));
    }

    @Test
    void assertCanManageDuplicate_Bidirectional_NeitherAllowed_Forbidden() {
        UUID candidateId = UUID.randomUUID();
        FileEntity sourceFile = FileEntity.builder().ownerUser(new User()).build();
        FileEntity candidateFile = FileEntity.builder().ownerUser(new User()).build();
        DuplicateCandidate dc = DuplicateCandidate.builder()
                .sourceFile(sourceFile)
                .candidateFile(candidateFile)
                .build();

        when(duplicateCandidateRepository.findById(candidateId)).thenReturn(Optional.of(dc));

        assertThrows(AccessDeniedException.class, () -> accessControlService.assertCanManageDuplicate(actorUserId, candidateId));
    }

    @Test
    void assertCanManageDuplicate_SkipDeletedSide_Forbidden() {
        UUID candidateId = UUID.randomUUID();
        User owner = new User();
        owner.setId(actorUserId);
        FileEntity sourceFile = FileEntity.builder().ownerUser(owner).deletedAt(OffsetDateTime.now()).build();
        FileEntity candidateFile = FileEntity.builder().ownerUser(new User()).build();
        DuplicateCandidate dc = DuplicateCandidate.builder()
                .sourceFile(sourceFile)
                .candidateFile(candidateFile)
                .build();

        when(duplicateCandidateRepository.findById(candidateId)).thenReturn(Optional.of(dc));

        assertThrows(AccessDeniedException.class, () -> accessControlService.assertCanManageDuplicate(actorUserId, candidateId));
    }

    @Test
    void assertCanViewContext_OwnerUser_Success() {
        assertDoesNotThrow(() -> accessControlService.assertCanViewContext(actorUserId, actorUserId, null));
    }

    @Test
    void assertCanViewContext_DifferentOwnerUser_Forbidden() {
        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertCanViewContext(actorUserId, UUID.randomUUID(), null));
    }

    @Test
    void assertCanCreateFolderInContext_OrganizationMemberRequiresPermission() {
        OrganizationMember member = new OrganizationMember();
        member.setRole(OrganizationMember.MemberRole.CONTRIBUTOR);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, actorUserId))
                .thenReturn(Optional.of(member));
        when(rolePermissionPolicyPort.hasPermission(
                OrganizationMember.MemberRole.CONTRIBUTOR,
                Permission.FOLDER_CREATE)).thenReturn(true);

        assertDoesNotThrow(() -> accessControlService.assertCanCreateFolderInContext(
                actorUserId,
                null,
                organizationId));
    }

    @Test
    void assertCanUploadToContext_OrganizationNonMember_Forbidden() {
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, actorUserId))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> accessControlService.assertCanUploadToContext(
                actorUserId,
                null,
                organizationId));
    }

    @Test
    void assertOrganizationPermission_NullActor_Forbidden() {
        assertThrows(AccessDeniedException.class,
                () -> accessControlService.assertOrganizationPermission(null, organizationId, Permission.FILE_VIEW));
    }

    @Test
    void assertCanViewDuplicates_MissingOwnerContext_Rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> accessControlService.assertCanViewDuplicates(actorUserId, null, null));
    }
}
