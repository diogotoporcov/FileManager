package com.filemanager.api.service;

import com.filemanager.api.auth.AccessControlService;
import com.filemanager.api.auth.Permission;
import com.filemanager.api.config.AppProperties;
import com.filemanager.api.dto.BoundedPageRequest;
import com.filemanager.api.dto.CursorPageResponse;
import com.filemanager.api.dto.DuplicateGroupResponse;
import com.filemanager.api.dto.DuplicateMatchResponse;
import com.filemanager.api.dto.DuplicateMethodMatchResponse;
import com.filemanager.api.dto.DuplicateSearchMethod;
import com.filemanager.api.dto.FileDuplicateSearchResponse;
import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileFingerprint;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.mapper.FileSummaryResponseMapper;
import com.filemanager.api.port.EmbeddingSimilarityCandidate;
import com.filemanager.api.port.EmbeddingSimilarityPairCandidate;
import com.filemanager.api.port.EmbeddingSimilarityPairSearchPort;
import com.filemanager.api.port.EmbeddingSimilarityPairSearchRequest;
import com.filemanager.api.port.EmbeddingSimilaritySearchPort;
import com.filemanager.api.port.EmbeddingSimilaritySearchRequest;
import com.filemanager.api.port.SimilarImageCandidate;
import com.filemanager.api.port.SimilarImagePairCandidate;
import com.filemanager.api.port.SimilarImagePairSearchPort;
import com.filemanager.api.port.SimilarImagePairSearchRequest;
import com.filemanager.api.port.SimilarImageSearchPort;
import com.filemanager.api.port.SimilarImageSearchRequest;
import com.filemanager.api.repository.FileEmbeddingRepository;
import com.filemanager.api.repository.FileFingerprintRepository;
import com.filemanager.api.repository.FileRepository;
import com.filemanager.api.repository.ImageFingerprintRepository;
import com.filemanager.api.repository.DuplicateHashGroupProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DuplicateSearchService {

    private static final Comparator<FileEntity> FILE_ORDER = Comparator
            .comparing(FileEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FileEntity::getId);

    private final FileRepository fileRepository;
    private final FileFingerprintRepository fileFingerprintRepository;
    private final ImageFingerprintRepository imageFingerprintRepository;
    private final FileEmbeddingRepository fileEmbeddingRepository;
    private final SimilarImageSearchPort similarImageSearchPort;
    private final SimilarImagePairSearchPort similarImagePairSearchPort;
    private final EmbeddingSimilaritySearchPort embeddingSimilaritySearchPort;
    private final EmbeddingSimilarityPairSearchPort embeddingSimilarityPairSearchPort;
    private final AccessControlService accessControlService;
    private final AppProperties appProperties;
    private final FileSummaryResponseMapper fileSummaryResponseMapper;

    @Transactional(readOnly = true)
    public List<DuplicateGroupResponse> findDuplicateGroups(
            UUID ownerUserId,
            UUID ownerOrganizationId,
            List<DuplicateSearchMethod> requestedMethods,
            UUID actorUserId) {
        return findDuplicateGroups(
                ownerUserId,
                ownerOrganizationId,
                requestedMethods,
                actorUserId,
                BoundedPageRequest.of(null, null))
                .getItems();
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<DuplicateGroupResponse> findDuplicateGroups(
            UUID ownerUserId,
            UUID ownerOrganizationId,
            List<DuplicateSearchMethod> requestedMethods,
            UUID actorUserId,
            BoundedPageRequest pageRequest) {
        OwnerContext ownerContext = OwnerContext.of(ownerUserId, ownerOrganizationId);
        accessControlService.assertCanViewDuplicates(actorUserId, ownerUserId, ownerOrganizationId);

        EnumSet<DuplicateSearchMethod> methods = normalizeMethods(requestedMethods);
        Map<UUID, GroupAccumulator> groups = new LinkedHashMap<>();
        BoundedPageRequest.SeekCursor cursor = pageRequest.decodedCursor();

        if (methods.contains(DuplicateSearchMethod.SHA256)) {
            collectSha256Groups(ownerContext, groups, pageRequest, cursor);
        }
        if (methods.contains(DuplicateSearchMethod.PHASH)) {
            collectPhashGroups(ownerContext, groups, pageRequest, cursor);
        }
        if (methods.contains(DuplicateSearchMethod.EMBEDDING)) {
            collectEmbeddingGroups(ownerContext, groups, pageRequest, cursor);
        }

        List<DuplicateGroupResponse> sortedGroups = groups.values().stream()
                .filter(group -> !group.matches.isEmpty())
                .map(GroupAccumulator::toResponse)
                .sorted(groupComparator())
                .toList();
        boolean hasMore = sortedGroups.size() > pageRequest.size();
        List<DuplicateGroupResponse> pageGroups = hasMore ? sortedGroups.subList(0, pageRequest.size()) : sortedGroups;
        DuplicateGroupResponse last = pageGroups.isEmpty() ? null : pageGroups.getLast();

        return CursorPageResponse.<DuplicateGroupResponse>builder()
                .items(pageGroups)
                .hasMore(hasMore)
                .nextCursor(nextGroupCursor(hasMore, last))
                .pageSize(pageRequest.size())
                .build();
    }

    @Transactional(readOnly = true)
    public FileDuplicateSearchResponse findDuplicatesForFile(
            UUID fileId,
            UUID actorUserId,
            List<DuplicateSearchMethod> requestedMethods) {
        return findDuplicatesForFile(fileId, actorUserId, requestedMethods, BoundedPageRequest.of(null, null));
    }

    @Transactional(readOnly = true)
    public FileDuplicateSearchResponse findDuplicatesForFile(
            UUID fileId,
            UUID actorUserId,
            List<DuplicateSearchMethod> requestedMethods,
            BoundedPageRequest pageRequest) {
        accessControlService.assertCanAccessFile(actorUserId, fileId, Permission.DUPLICATE_VIEW);

        FileEntity sourceFile = fileRepository.findByIdAndDeletedAtIsNull(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        OwnerContext ownerContext = OwnerContext.fromFile(sourceFile);
        EnumSet<DuplicateSearchMethod> methods = normalizeMethods(requestedMethods);
        Map<UUID, MatchAccumulator> matches = new LinkedHashMap<>();
        BoundedPageRequest.SeekCursor cursor = pageRequest.decodedCursor();

        if (methods.contains(DuplicateSearchMethod.SHA256)) {
            collectSha256Matches(sourceFile, ownerContext, matches, pageRequest.fetchSize(), cursor);
        }
        if (methods.contains(DuplicateSearchMethod.PHASH)) {
            collectPhashMatches(sourceFile, ownerContext, matches, pageRequest.fetchSize());
        }
        if (methods.contains(DuplicateSearchMethod.EMBEDDING)) {
            collectEmbeddingMatches(sourceFile, ownerContext, matches, pageRequest.fetchSize());
        }

        List<DuplicateMatchResponse> sortedMatches = matches.values().stream()
                .map(MatchAccumulator::toResponse)
                .sorted(matchComparator())
                .toList();
        boolean hasMore = sortedMatches.size() > pageRequest.size();
        List<DuplicateMatchResponse> responses = hasMore
                ? sortedMatches.subList(0, pageRequest.size())
                : sortedMatches;
        DuplicateMatchResponse last = responses.isEmpty() ? null : responses.getLast();

        return FileDuplicateSearchResponse.builder()
                .originalFile(fileSummaryResponseMapper.toSummary(sourceFile))
                .matches(responses)
                .hasMore(hasMore)
                .nextCursor(nextMatchCursor(hasMore, last))
                .pageSize(pageRequest.size())
                .build();
    }

    private void collectSha256Groups(
            OwnerContext ownerContext,
            Map<UUID, GroupAccumulator> groups,
            BoundedPageRequest pageRequest,
            BoundedPageRequest.SeekCursor cursor) {
        List<DuplicateHashGroupProjection> hashGroups = ownerContext.ownerUserId() != null
                ? fileFingerprintRepository.findDuplicateSha256GroupsForOwnerUser(
                        FileFingerprint.FingerprintAlgorithm.SHA256.name(),
                        ownerContext.ownerUserId(),
                        cursor == null ? null : cursor.createdAt(),
                        cursor == null ? null : cursor.id(),
                        pageRequest.fetchSize())
                : fileFingerprintRepository.findDuplicateSha256GroupsForOwnerOrganization(
                        FileFingerprint.FingerprintAlgorithm.SHA256.name(),
                        ownerContext.ownerOrganizationId(),
                        cursor == null ? null : cursor.createdAt(),
                        cursor == null ? null : cursor.id(),
                        pageRequest.fetchSize());
        Set<String> hashValues = hashGroups.stream()
                .map(DuplicateHashGroupProjection::getHashValue)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (hashValues.isEmpty()) {
            return;
        }

        List<FileFingerprint> fingerprints = ownerContext.ownerUserId() != null
                ? fileFingerprintRepository.findByOwnerUserAndAlgorithmAndHashValueIn(
                        FileFingerprint.FingerprintAlgorithm.SHA256,
                        ownerContext.ownerUserId(),
                        hashValues)
                : fileFingerprintRepository.findByOwnerOrganizationAndAlgorithmAndHashValueIn(
                        FileFingerprint.FingerprintAlgorithm.SHA256,
                        ownerContext.ownerOrganizationId(),
                        hashValues);

        Map<String, List<FileFingerprint>> byHash = fingerprints.stream()
                .collect(Collectors.groupingBy(
                        FileFingerprint::getHashValue,
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));

        for (List<FileFingerprint> duplicateFingerprints : byHash.values()) {
            if (duplicateFingerprints.size() < 2) {
                continue;
            }
            List<FileEntity> files = duplicateFingerprints.stream()
                    .map(FileFingerprint::getFile)
                    .sorted(FILE_ORDER)
                    .toList();
            FileEntity original = files.getFirst();
            GroupAccumulator group = groups.computeIfAbsent(original.getId(), ignored -> new GroupAccumulator(original));
            files.stream()
                    .skip(1)
                    .forEach(file -> group.add(file, DuplicateSearchMethod.SHA256, 0.0, 1.0));
        }
    }

    private void collectPhashGroups(
            OwnerContext ownerContext,
            Map<UUID, GroupAccumulator> groups,
            BoundedPageRequest pageRequest,
            BoundedPageRequest.SeekCursor cursor) {
        AppProperties.Phash phashProperties = appProperties.getPhash();
        List<SimilarImagePairCandidate> pairs = similarImagePairSearchPort.search(new SimilarImagePairSearchRequest(
                ownerContext.ownerUserId(),
                ownerContext.ownerOrganizationId(),
                phashProperties.getThreshold(),
                Math.min(phashProperties.getMaxCandidates(), pageRequest.fetchSize()),
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.id()));
        Map<UUID, FileEntity> files = fetchActiveFiles(pairFileIds(pairs));

        for (SimilarImagePairCandidate pair : pairs) {
            FileEntity sourceFile = files.get(pair.sourceFileId());
            FileEntity candidateFile = files.get(pair.candidateFileId());
            if (isEligibleMatch(sourceFile, candidateFile, ownerContext)) {
                addPairToGroups(
                        groups,
                        sourceFile,
                        candidateFile,
                        DuplicateSearchMethod.PHASH,
                        (double) pair.distance(),
                        phashConfidence(pair.distance()));
            }
        }
    }

    private void collectEmbeddingGroups(
            OwnerContext ownerContext,
            Map<UUID, GroupAccumulator> groups,
            BoundedPageRequest pageRequest,
            BoundedPageRequest.SeekCursor cursor) {
        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        if (!embeddingProperties.isEnabled()) {
            return;
        }

        List<EmbeddingSimilarityPairCandidate> pairs = embeddingSimilarityPairSearchPort.search(
                new EmbeddingSimilarityPairSearchRequest(
                        ownerContext.ownerUserId(),
                        ownerContext.ownerOrganizationId(),
                        embeddingProperties.getModelName(),
                        embeddingProperties.getModelVersion(),
                        embeddingProperties.getSimilarityThreshold(),
                        Math.min(embeddingProperties.getMaxCandidates(), pageRequest.fetchSize()),
                        cursor == null ? null : cursor.createdAt(),
                        cursor == null ? null : cursor.id()));
        Map<UUID, FileEntity> files = fetchActiveFiles(pairFileIds(pairs));

        for (EmbeddingSimilarityPairCandidate pair : pairs) {
            FileEntity sourceFile = files.get(pair.sourceFileId());
            FileEntity candidateFile = files.get(pair.candidateFileId());
            if (isEligibleMatch(sourceFile, candidateFile, ownerContext)) {
                addPairToGroups(
                        groups,
                        sourceFile,
                        candidateFile,
                        DuplicateSearchMethod.EMBEDDING,
                        pair.distance(),
                        embeddingConfidence(pair.distance()));
            }
        }
    }

    private void collectSha256Matches(
            FileEntity sourceFile,
            OwnerContext ownerContext,
            Map<UUID, MatchAccumulator> matches,
            int maxRows,
            BoundedPageRequest.SeekCursor cursor) {
        Optional<FileFingerprint> sourceFingerprint = fileFingerprintRepository.findByFileIdAndAlgorithm(
                sourceFile.getId(),
                FileFingerprint.FingerprintAlgorithm.SHA256);
        if (sourceFingerprint.isEmpty()) {
            return;
        }

        PageRequest pageRequest = PageRequest.of(
                0,
                maxRows,
                Sort.by(Sort.Order.asc("file.createdAt"), Sort.Order.asc("file.id")));
        List<FileFingerprint> fingerprints = ownerContext.ownerUserId() != null
                ? fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerUserIdAndFileDeletedAtIsNull(
                        FileFingerprint.FingerprintAlgorithm.SHA256,
                        sourceFingerprint.get().getHashValue(),
                        ownerContext.ownerUserId(),
                        pageRequest)
                : fileFingerprintRepository.findByAlgorithmAndHashValueAndFileOwnerOrganizationIdAndFileDeletedAtIsNull(
                        FileFingerprint.FingerprintAlgorithm.SHA256,
                        sourceFingerprint.get().getHashValue(),
                        ownerContext.ownerOrganizationId(),
                        pageRequest);

        for (FileFingerprint fingerprint : fingerprints) {
            FileEntity candidateFile = fingerprint.getFile();
            if (isEligibleMatch(sourceFile, candidateFile, ownerContext) && isAfterCursor(candidateFile, cursor)) {
                addMatch(matches, candidateFile, DuplicateSearchMethod.SHA256, 0.0, 1.0);
            }
        }
    }

    private void collectPhashMatches(
            FileEntity sourceFile,
            OwnerContext ownerContext,
            Map<UUID, MatchAccumulator> matches,
            int maxRows) {
        imageFingerprintRepository.findByFileId(sourceFile.getId())
                .ifPresent(fingerprint -> {
                    Map<UUID, SimilarImageCandidate> candidates = searchPhashCandidates(
                            sourceFile,
                            ownerContext,
                            fingerprint.getPhash(),
                            maxRows);
                    Map<UUID, FileEntity> candidateFiles = fetchActiveFiles(candidates.keySet());
                    for (SimilarImageCandidate candidate : candidates.values()) {
                        FileEntity candidateFile = candidateFiles.get(candidate.fileId());
                        if (isEligibleMatch(sourceFile, candidateFile, ownerContext)) {
                            addMatch(
                                    matches,
                                    candidateFile,
                                    DuplicateSearchMethod.PHASH,
                                    (double) candidate.distance(),
                                    phashConfidence(candidate.distance()));
                        }
                    }
                });
    }

    private void collectEmbeddingMatches(
            FileEntity sourceFile,
            OwnerContext ownerContext,
            Map<UUID, MatchAccumulator> matches,
            int maxRows) {
        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        if (!embeddingProperties.isEnabled()) {
            return;
        }

        fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                        sourceFile.getId(),
                        embeddingProperties.getModelName(),
                        embeddingProperties.getModelVersion())
                .ifPresent(embedding -> {
                    Map<UUID, EmbeddingSimilarityCandidate> candidates = searchEmbeddingCandidates(
                            sourceFile,
                            ownerContext,
                            embedding.getModelName(),
                            embedding.getModelVersion(),
                            maxRows);
                    Map<UUID, FileEntity> candidateFiles = fetchActiveFiles(candidates.keySet());
                    for (EmbeddingSimilarityCandidate candidate : candidates.values()) {
                        FileEntity candidateFile = candidateFiles.get(candidate.fileId());
                        if (isEligibleMatch(sourceFile, candidateFile, ownerContext)) {
                            addMatch(
                                    matches,
                                    candidateFile,
                                    DuplicateSearchMethod.EMBEDDING,
                                    candidate.distance(),
                                    embeddingConfidence(candidate.distance()));
                        }
                    }
                });
    }

    private Map<UUID, SimilarImageCandidate> searchPhashCandidates(
            FileEntity sourceFile,
            OwnerContext ownerContext,
            String phash,
            int maxRows) {
        AppProperties.Phash phashProperties = appProperties.getPhash();
        return similarImageSearchPort.search(new SimilarImageSearchRequest(
                        sourceFile.getId(),
                        ownerContext.ownerUserId(),
                        ownerContext.ownerOrganizationId(),
                        phash,
                        phashProperties.getThreshold(),
                        Math.min(phashProperties.getMaxCandidates(), maxRows)))
                .stream()
                .collect(Collectors.toMap(
                        SimilarImageCandidate::fileId,
                        Function.identity(),
                        this::closestPhash,
                        LinkedHashMap::new));
    }

    private Map<UUID, EmbeddingSimilarityCandidate> searchEmbeddingCandidates(
            FileEntity sourceFile,
            OwnerContext ownerContext,
            String modelName,
            String modelVersion,
            int maxRows) {
        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        return embeddingSimilaritySearchPort.search(new EmbeddingSimilaritySearchRequest(
                        sourceFile.getId(),
                        ownerContext.ownerUserId(),
                        ownerContext.ownerOrganizationId(),
                        modelName,
                        modelVersion,
                        embeddingProperties.getSimilarityThreshold(),
                        Math.min(embeddingProperties.getMaxCandidates(), maxRows)))
                .stream()
                .collect(Collectors.toMap(
                        EmbeddingSimilarityCandidate::fileId,
                        Function.identity(),
                        this::closestEmbedding,
                        LinkedHashMap::new));
    }

    private SimilarImageCandidate closestPhash(SimilarImageCandidate left, SimilarImageCandidate right) {
        return left.distance() <= right.distance() ? left : right;
    }

    private EmbeddingSimilarityCandidate closestEmbedding(
            EmbeddingSimilarityCandidate left,
            EmbeddingSimilarityCandidate right) {
        return left.distance() <= right.distance() ? left : right;
    }

    private Map<UUID, FileEntity> fetchActiveFiles(Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return fileRepository.findByIdInAndDeletedAtIsNull(fileIds).stream()
                .collect(Collectors.toMap(FileEntity::getId, Function.identity()));
    }

    private void addPairToGroups(
            Map<UUID, GroupAccumulator> groups,
            FileEntity left,
            FileEntity right,
            DuplicateSearchMethod method,
            Double distance,
            Double confidenceScore) {
        FileEntity original = FILE_ORDER.compare(left, right) <= 0 ? left : right;
        FileEntity duplicate = Objects.equals(original.getId(), left.getId()) ? right : left;
        groups.computeIfAbsent(original.getId(), ignored -> new GroupAccumulator(original))
                .add(duplicate, method, distance, confidenceScore);
    }

    private void addMatch(
            Map<UUID, MatchAccumulator> matches,
            FileEntity file,
            DuplicateSearchMethod method,
            Double distance,
            Double confidenceScore) {
        matches.computeIfAbsent(file.getId(), ignored -> new MatchAccumulator(file))
                .add(method, distance, confidenceScore);
    }

    private Set<UUID> pairFileIds(List<?> pairs) {
        Set<UUID> fileIds = new java.util.LinkedHashSet<>();
        for (Object pair : pairs) {
            if (pair instanceof SimilarImagePairCandidate similarPair) {
                fileIds.add(similarPair.sourceFileId());
                fileIds.add(similarPair.candidateFileId());
            } else if (pair instanceof EmbeddingSimilarityPairCandidate embeddingPair) {
                fileIds.add(embeddingPair.sourceFileId());
                fileIds.add(embeddingPair.candidateFileId());
            }
        }
        return fileIds;
    }

    private boolean isEligibleMatch(FileEntity sourceFile, FileEntity candidateFile, OwnerContext ownerContext) {
        return sourceFile != null
                && candidateFile != null
                && !Objects.equals(sourceFile.getId(), candidateFile.getId())
                && sourceFile.getDeletedAt() == null
                && candidateFile.getDeletedAt() == null
                && ownerContext.contains(sourceFile)
                && ownerContext.contains(candidateFile);
    }

    private boolean isAfterCursor(FileEntity file, BoundedPageRequest.SeekCursor cursor) {
        if (cursor == null) {
            return true;
        }
        int createdAtComparison = file.getCreatedAt().compareTo(cursor.createdAt());
        return createdAtComparison > 0
                || (createdAtComparison == 0 && file.getId().compareTo(cursor.id()) > 0);
    }

    private EnumSet<DuplicateSearchMethod> normalizeMethods(List<DuplicateSearchMethod> methods) {
        if (methods == null || methods.isEmpty()) {
            return EnumSet.of(DuplicateSearchMethod.SHA256);
        }
        return EnumSet.copyOf(methods);
    }

    private String nextGroupCursor(boolean hasMore, DuplicateGroupResponse last) {
        if (!hasMore || last == null || last.getOriginalFile() == null) {
            return null;
        }
        return BoundedPageRequest.encodeCursor(last.getOriginalFile().getCreatedAt(), last.getOriginalFile().getId());
    }

    private String nextMatchCursor(boolean hasMore, DuplicateMatchResponse last) {
        if (!hasMore || last == null || last.getFile() == null) {
            return null;
        }
        return BoundedPageRequest.encodeCursor(last.getFile().getCreatedAt(), last.getFile().getId());
    }

    private Comparator<DuplicateMatchResponse> matchComparator() {
        return Comparator
                .comparing((DuplicateMatchResponse match) -> methodStrength(match.getBestMethod()))
                .thenComparing(match -> bestDistance(match, DuplicateSearchMethod.PHASH), Comparator.nullsLast(Double::compareTo))
                .thenComparing(match -> bestDistance(match, DuplicateSearchMethod.EMBEDDING), Comparator.nullsLast(Double::compareTo))
                .thenComparing(match -> match.getFile().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(match -> match.getFile().getId());
    }

    private Comparator<DuplicateGroupResponse> groupComparator() {
        return Comparator
                .comparing((DuplicateGroupResponse group) -> methodStrength(group.getMethod()))
                .thenComparing(group -> group.getOriginalFile().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(group -> group.getOriginalFile().getId());
    }

    private Double bestDistance(DuplicateMatchResponse match, DuplicateSearchMethod method) {
        return match.getMatches().stream()
                .filter(evidence -> evidence.getMethod() == method)
                .map(DuplicateMethodMatchResponse::getDistance)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);
    }

    private int methodStrength(DuplicateSearchMethod method) {
        return switch (method) {
            case SHA256 -> 0;
            case PHASH -> 1;
            case EMBEDDING -> 2;
        };
    }

    private double phashConfidence(int distance) {
        return Math.clamp(1.0 - ((double) distance / 64.0), 0.0, 1.0);
    }

    private double embeddingConfidence(double distance) {
        return Math.clamp(1.0 - distance, 0.0, 1.0);
    }

    private record OwnerContext(UUID ownerUserId, UUID ownerOrganizationId) {
        static OwnerContext of(UUID ownerUserId, UUID ownerOrganizationId) {
            if ((ownerUserId == null && ownerOrganizationId == null)
                    || (ownerUserId != null && ownerOrganizationId != null)) {
                throw new IllegalArgumentException("Exactly one of ownerUserId or ownerOrganizationId must be provided");
            }
            return new OwnerContext(ownerUserId, ownerOrganizationId);
        }

        static OwnerContext fromFile(FileEntity file) {
            UUID ownerUserId = file.getOwnerUser() == null ? null : file.getOwnerUser().getId();
            UUID ownerOrganizationId = file.getOwnerOrganization() == null ? null : file.getOwnerOrganization().getId();
            return of(ownerUserId, ownerOrganizationId);
        }

        boolean contains(FileEntity file) {
            if (ownerUserId != null) {
                return file.getOwnerUser() != null && ownerUserId.equals(file.getOwnerUser().getId());
            }
            return file.getOwnerOrganization() != null
                    && ownerOrganizationId.equals(file.getOwnerOrganization().getId());
        }
    }

    private final class GroupAccumulator {
        private final FileEntity original;
        private final Map<UUID, MatchAccumulator> matches = new LinkedHashMap<>();

        private GroupAccumulator(FileEntity original) {
            this.original = original;
        }

        private void add(FileEntity file, DuplicateSearchMethod method, Double distance, Double confidenceScore) {
            addMatch(matches, file, method, distance, confidenceScore);
        }

        private DuplicateGroupResponse toResponse() {
            List<DuplicateMatchResponse> duplicates = matches.values().stream()
                    .map(MatchAccumulator::toResponse)
                    .sorted(matchComparator())
                    .toList();
            DuplicateSearchMethod method = duplicates.stream()
                    .map(DuplicateMatchResponse::getBestMethod)
                    .min(Comparator.comparingInt(DuplicateSearchService.this::methodStrength))
                    .orElse(DuplicateSearchMethod.SHA256);
            return DuplicateGroupResponse.builder()
                    .method(method)
                    .originalFile(fileSummaryResponseMapper.toSummary(original))
                    .duplicates(duplicates)
                    .groupSize(duplicates.size() + 1)
                    .build();
        }
    }

    private final class MatchAccumulator {
        private final FileEntity file;
        private final Map<DuplicateSearchMethod, DuplicateMethodMatchResponse> evidence = new EnumMap<>(DuplicateSearchMethod.class);

        private MatchAccumulator(FileEntity file) {
            this.file = file;
        }

        private void add(DuplicateSearchMethod method, Double distance, Double confidenceScore) {
            DuplicateMethodMatchResponse next = DuplicateMethodMatchResponse.builder()
                    .method(method)
                    .distance(distance)
                    .confidenceScore(confidenceScore)
                    .build();
            evidence.merge(method, next, this::bestEvidence);
        }

        private DuplicateMethodMatchResponse bestEvidence(
                DuplicateMethodMatchResponse existing,
                DuplicateMethodMatchResponse candidate) {
            if (existing.getDistance() == null) {
                return existing;
            }
            if (candidate.getDistance() == null) {
                return candidate;
            }
            return existing.getDistance() <= candidate.getDistance() ? existing : candidate;
        }

        private DuplicateMatchResponse toResponse() {
            List<DuplicateMethodMatchResponse> matches = evidence.values().stream()
                    .sorted(Comparator.comparingInt(evidence -> methodStrength(evidence.getMethod())))
                    .toList();
            DuplicateSearchMethod bestMethod = matches.stream()
                    .map(DuplicateMethodMatchResponse::getMethod)
                    .min(Comparator.comparingInt(DuplicateSearchService.this::methodStrength))
                    .orElse(DuplicateSearchMethod.SHA256);
            return DuplicateMatchResponse.builder()
                    .file(fileSummaryResponseMapper.toSummary(file))
                    .bestMethod(bestMethod)
                    .matches(matches)
                    .build();
        }
    }
}
