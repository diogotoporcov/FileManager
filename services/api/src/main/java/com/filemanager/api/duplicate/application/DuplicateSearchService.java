package com.filemanager.api.duplicate.application;

import com.filemanager.api.config.AppProperties;
import com.filemanager.api.duplicate.domain.DuplicateConfidence;
import com.filemanager.api.duplicate.domain.DuplicateEvidenceType;
import com.filemanager.api.duplicate.domain.DuplicateMethodStatus;
import com.filemanager.api.duplicate.domain.DuplicateSearchMethod;
import com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupFileProjection;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupKeyProjection;
import com.filemanager.api.duplicate.persistence.ExactDuplicateGroupRepository;
import com.filemanager.api.duplicate.persistence.ImageEmbeddingDuplicateCandidateRepository;
import com.filemanager.api.duplicate.persistence.ImagePhashDuplicateCandidateRepository;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchRequest;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse.DuplicateGroupEvidenceResponse;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse.DuplicateGroupFileResponse;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse.DuplicateGroupMethodResultResponse;
import com.filemanager.api.duplicate.web.DuplicateGroupSearchResponse.DuplicateGroupResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchPageRequest;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse.DuplicateEvidenceResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse.DuplicateMatchResponse;
import com.filemanager.api.duplicate.web.DuplicateSearchResponse.DuplicateMethodResultResponse;
import com.filemanager.api.exception.AccessDeniedException;
import com.filemanager.api.exception.ResourceNotFoundException;
import com.filemanager.api.file.domain.FileEntity;
import com.filemanager.api.file.persistence.FileRepository;
import com.filemanager.api.observability.application.FileManagerMetrics;
import com.filemanager.api.processing.domain.result.FileFingerprint.FingerprintAlgorithm;
import com.filemanager.api.processing.persistence.result.AudioFingerprintRepository;
import com.filemanager.api.processing.persistence.result.FileEmbeddingRepository;
import com.filemanager.api.processing.persistence.result.FileFingerprintRepository;
import com.filemanager.api.processing.persistence.result.ImageFingerprintRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DuplicateSearchService {
    private static final FingerprintAlgorithm EXACT_ALGORITHM = FingerprintAlgorithm.SHA256;
    private static final List<DuplicateSearchMethod> DEFAULT_METHODS = List.of(DuplicateSearchMethod.EXACT);

    private final FileRepository fileRepository;
    private final FileFingerprintRepository fileFingerprintRepository;
    private final ImageFingerprintRepository imageFingerprintRepository;
    private final FileEmbeddingRepository fileEmbeddingRepository;
    private final AudioFingerprintRepository audioFingerprintRepository;
    private final ExactDuplicateCandidateRepository exactDuplicateCandidateRepository;
    private final ExactDuplicateGroupRepository exactDuplicateGroupRepository;
    private final ImagePhashDuplicateCandidateRepository imagePhashDuplicateCandidateRepository;
    private final ImageEmbeddingDuplicateCandidateRepository imageEmbeddingDuplicateCandidateRepository;
    private final AudioDuplicateCandidateRepository audioDuplicateCandidateRepository;
    private final DuplicateDetectionProperties properties;
    private final AppProperties appProperties;
    private final FileManagerMetrics fileManagerMetrics;

    @Transactional(readOnly = true)
    public DuplicateSearchResponse searchDuplicatesForFile(
            UUID sourceFileId,
            Collection<DuplicateSearchMethod> requestedMethods,
            UUID actorUserId) {
        return searchDuplicatesForFile(sourceFileId, requestedMethods, actorUserId, DuplicateSearchPageRequest.defaults());
    }

    @Transactional(readOnly = true)
    public DuplicateSearchResponse searchDuplicatesForFile(
            UUID sourceFileId,
            Collection<DuplicateSearchMethod> requestedMethods,
            UUID actorUserId,
            DuplicateSearchPageRequest pageRequest) {
        long started = System.nanoTime();
        String status = "success";
        try {
            List<DuplicateSearchMethod> methods = normalizeMethods(requestedMethods);
            SearchCursor cursor = validateAndDecodeCursor(requestedMethods, methods, pageRequest);
            FileEntity sourceFile = loadOwnedSourceFile(sourceFileId, actorUserId);
            fileManagerMetrics.recordDuplicateSearchRequested();

            List<DuplicateMethodResultResponse> methodResponses = methods.stream()
                    .map(method -> searchByMethod(method, sourceFile, actorUserId, pageRequest, cursor))
                    .toList();

            return new DuplicateSearchResponse(sourceFileId, methodResponses);
        } catch (RuntimeException ex) {
            status = "failure";
            throw ex;
        } finally {
            fileManagerMetrics.recordOperationDuration(
                    "duplicate.search",
                    status,
                    Duration.ofNanos(System.nanoTime() - started));
        }
    }

    @Transactional(readOnly = true)
    public DuplicateGroupSearchResponse searchGroups(DuplicateGroupSearchRequest request, UUID actorUserId) {
        long started = System.nanoTime();
        String status = "success";
        try {
            List<DuplicateSearchMethod> methods = normalizeMethods(request == null ? null : request.methods());
            int requestedLimit = request == null || request.limit() == null
                    ? properties.getExact().getMaxGroups()
                    : request.limit();
            UUID folderId = request == null ? null : request.folderId();
            String mimeType = normalizeOptionalText(request == null ? null : request.mimeType());
            DuplicateConfidence minConfidence = request == null || request.minConfidence() == null
                    ? DuplicateConfidence.SIMILAR
                    : request.minConfidence();
            fileManagerMetrics.recordDuplicateGroupsRequested();

            List<DuplicateGroupMethodResultResponse> methodResponses = methods.stream()
                    .map(method -> searchGroupsByMethod(method, actorUserId, requestedLimit, folderId, mimeType, minConfidence))
                    .toList();

            return new DuplicateGroupSearchResponse(methodResponses, null);
        } catch (RuntimeException ex) {
            status = "failure";
            throw ex;
        } finally {
            fileManagerMetrics.recordOperationDuration(
                    "duplicate.groups",
                    status,
                    Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private DuplicateMethodResultResponse searchByMethod(
            DuplicateSearchMethod method,
            FileEntity sourceFile,
            UUID actorUserId,
            DuplicateSearchPageRequest pageRequest,
            SearchCursor cursor) {
        DuplicateMethodResultResponse response = switch (method) {
            case EXACT -> searchExact(sourceFile, actorUserId, pageRequest, cursor);
            case IMAGE_PHASH -> searchImagePhash(sourceFile, actorUserId, pageRequest, cursor);
            case IMAGE_EMBEDDING -> searchImageEmbedding(sourceFile, actorUserId, pageRequest, cursor);
            case AUDIO_FINGERPRINT -> searchAudioFingerprint(sourceFile, actorUserId, pageRequest, cursor);
        };
        recordMethodMetrics(method, response.status(), response.matches().size());

        return response;
    }

    private DuplicateGroupMethodResultResponse searchGroupsByMethod(
            DuplicateSearchMethod method,
            UUID actorUserId,
            int requestedLimit,
            UUID folderId,
            String mimeType,
            DuplicateConfidence minConfidence) {
        DuplicateGroupMethodResultResponse response = switch (method) {
            case EXACT -> searchExactGroups(actorUserId, requestedLimit, folderId, mimeType, minConfidence);
            case IMAGE_PHASH, IMAGE_EMBEDDING, AUDIO_FINGERPRINT -> groupMethodResult(
                    method,
                    DuplicateMethodStatus.NOT_SUPPORTED_FOR_GROUPED_SEARCH_YET,
                    List.of());
        };
        fileManagerMetrics.recordDuplicateGroupsReturned(method.name(), response.groups().size());

        return response;
    }

    private DuplicateMethodResultResponse searchExact(
            FileEntity sourceFile,
            UUID actorUserId,
            DuplicateSearchPageRequest pageRequest,
            SearchCursor cursor) {
        int pageSize = pageSize(pageRequest, properties.getExact().getPageSize());
        SearchCursor methodCursor = methodCursor(cursor, DuplicateSearchMethod.EXACT);
        if (!properties.getExact().isEnabled()) {
            return methodResult(DuplicateSearchMethod.EXACT, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of(), pageSize);
        }

        return fileFingerprintRepository.findByFileIdAndAlgorithm(sourceFile.getId(), EXACT_ALGORITHM)
                .map(sourceFingerprint -> {
                    List<com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateProjection> candidates =
                            exactDuplicateCandidateRepository.findCandidates(
                                    actorUserId,
                                    sourceFile.getId(),
                                    sourceFingerprint.getAlgorithm(),
                                    sourceFingerprint.getHashValue(),
                                    methodCursor == null ? null : methodCursor.createdAt(),
                                    methodCursor == null ? null : methodCursor.fileId(),
                                    PageRequest.of(0, pageSize + 1));
                    List<DuplicateMatchResponse> matches = candidates.stream()
                            .map(candidate -> exactMatch(candidate.fileId(), candidate.algorithm().name()))
                            .toList();
                    return pagedMethodResult(
                            DuplicateSearchMethod.EXACT,
                            DuplicateMethodStatus.COMPLETED,
                            matches,
                            pageSize,
                            exactNextCursor(candidates, pageSize));
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.EXACT,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of(),
                        pageSize));
    }

    private DuplicateMethodResultResponse searchImagePhash(
            FileEntity sourceFile,
            UUID actorUserId,
            DuplicateSearchPageRequest pageRequest,
            SearchCursor cursor) {
        int pageSize = pageSize(pageRequest, properties.getImagePhash().getPageSize());
        SearchCursor methodCursor = methodCursor(cursor, DuplicateSearchMethod.IMAGE_PHASH);
        if (!properties.getImagePhash().isEnabled()) {
            return methodResult(DuplicateSearchMethod.IMAGE_PHASH, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of(), pageSize);
        }

        if (!isImage(sourceFile)) {
            return methodResult(
                    DuplicateSearchMethod.IMAGE_PHASH,
                    DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE,
                    List.of(),
                    pageSize);
        }

        return imageFingerprintRepository.findByFileId(sourceFile.getId())
                .map(sourceFingerprint -> {
                    List<com.filemanager.api.duplicate.persistence.PhashDuplicateCandidateProjection> candidates =
                            imagePhashDuplicateCandidateRepository.findCandidates(
                                    actorUserId,
                                    sourceFile.getId(),
                                    sourceFingerprint.getPhash(),
                                    properties.getImagePhash().getMaxDistance(),
                                    methodCursor == null ? null : methodCursor.distance().intValue(),
                                    methodCursor == null ? null : methodCursor.createdAt().toString(),
                                    methodCursor == null ? null : methodCursor.fileId().toString(),
                                    pageSize + 1);
                    List<DuplicateMatchResponse> matches = candidates.stream()
                            .map(candidate -> phashMatch(candidate.getFileId(), candidate.getDistance()))
                            .toList();
                    return pagedMethodResult(
                            DuplicateSearchMethod.IMAGE_PHASH,
                            DuplicateMethodStatus.COMPLETED,
                            matches,
                            pageSize,
                            phashNextCursor(candidates, pageSize));
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of(),
                        pageSize));
    }

    private DuplicateMethodResultResponse searchImageEmbedding(
            FileEntity sourceFile,
            UUID actorUserId,
            DuplicateSearchPageRequest pageRequest,
            SearchCursor cursor) {
        int pageSize = pageSize(pageRequest, properties.getImageEmbedding().getPageSize());
        SearchCursor methodCursor = methodCursor(cursor, DuplicateSearchMethod.IMAGE_EMBEDDING);
        if (!properties.getImageEmbedding().isEnabled()) {
            return methodResult(DuplicateSearchMethod.IMAGE_EMBEDDING, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of(), pageSize);
        }

        if (!isImage(sourceFile)) {
            return methodResult(
                    DuplicateSearchMethod.IMAGE_EMBEDDING,
                    DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE,
                    List.of(),
                    pageSize);
        }

        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        return fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                        sourceFile.getId(),
                        embeddingProperties.getModelName(),
                        embeddingProperties.getModelVersion())
                .filter(sourceEmbedding -> Objects.equals(sourceEmbedding.getDimension(), embeddingProperties.getDimension()))
                .map(sourceEmbedding -> {
                    AdaptiveEmbeddingCandidates candidates = findAdaptiveEmbeddingCandidates(
                            actorUserId,
                            sourceFile.getId(),
                            sourceEmbedding.getModelName(),
                            sourceEmbedding.getModelVersion(),
                            sourceEmbedding.getDimension(),
                            vectorLiteral(sourceEmbedding.getEmbedding()),
                            methodCursor,
                            pageSize);
                    List<DuplicateMatchResponse> matches = candidates.stream()
                            .map(candidate -> embeddingMatch(
                                    candidate.getFileId(),
                                    candidate.getDistance(),
                                    DuplicateEvidenceType.IMAGE_EMBEDDING))
                            .toList();
                    return pagedMethodResult(
                            DuplicateSearchMethod.IMAGE_EMBEDDING,
                            DuplicateMethodStatus.COMPLETED,
                            matches,
                            pageSize,
                            embeddingNextCursor(candidates.matches(), pageSize, candidates.forceHasMore()),
                            candidates.forceHasMore());
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.IMAGE_EMBEDDING,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of(),
                        pageSize));
    }

    private DuplicateMethodResultResponse searchAudioFingerprint(
            FileEntity sourceFile,
            UUID actorUserId,
            DuplicateSearchPageRequest pageRequest,
            SearchCursor cursor) {
        int pageSize = pageSize(pageRequest, properties.getAudioFingerprint().getPageSize());
        SearchCursor methodCursor = methodCursor(cursor, DuplicateSearchMethod.AUDIO_FINGERPRINT);
        if (!properties.getAudioFingerprint().isEnabled()) {
            return methodResult(DuplicateSearchMethod.AUDIO_FINGERPRINT, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of(), pageSize);
        }

        if (!isAudio(sourceFile)) {
            return methodResult(
                    DuplicateSearchMethod.AUDIO_FINGERPRINT,
                    DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE,
                    List.of(),
                    pageSize);
        }

        return audioFingerprintRepository.findByFileId(sourceFile.getId())
                .map(sourceFingerprint -> {
                    List<com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateProjection> candidates =
                            audioDuplicateCandidateRepository.findCandidates(
                                    actorUserId,
                                    sourceFile.getId(),
                                    sourceFingerprint.getFingerprintAlgorithm(),
                                    sourceFingerprint.getFingerprintVersion(),
                                    sourceFingerprint.getFingerprintHash(),
                                    methodCursor == null ? null : methodCursor.createdAt(),
                                    methodCursor == null ? null : methodCursor.fileId(),
                                    PageRequest.of(0, pageSize + 1));
                    List<DuplicateMatchResponse> matches = candidates.stream()
                            .map(candidate -> audioMatch(candidate.fileId()))
                            .toList();
                    return pagedMethodResult(
                            DuplicateSearchMethod.AUDIO_FINGERPRINT,
                            DuplicateMethodStatus.COMPLETED,
                            matches,
                            pageSize,
                            audioNextCursor(candidates, pageSize));
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.AUDIO_FINGERPRINT,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of(),
                        pageSize));
    }

    private DuplicateGroupMethodResultResponse searchExactGroups(
            UUID actorUserId,
            int requestedLimit,
            UUID folderId,
            String mimeType,
            DuplicateConfidence minConfidence) {
        if (!properties.getExact().isEnabled()) {
            return groupMethodResult(DuplicateSearchMethod.EXACT, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of());
        }

        if (!allowsConfidence(minConfidence, DuplicateConfidence.EXACT)) {
            return groupMethodResult(DuplicateSearchMethod.EXACT, DuplicateMethodStatus.COMPLETED, List.of());
        }

        int limit = Math.min(requestedLimit, properties.getExact().getMaxGroups());
        List<ExactDuplicateGroupKeyProjection> keys = hasSummaryCompatibleFilters(folderId, mimeType)
                ? exactDuplicateGroupRepository.findOwnerGroupKeys(actorUserId, PageRequest.of(0, limit))
                : exactDuplicateCandidateRepository.findGroupKeys(
                        actorUserId,
                        folderId,
                        mimeType,
                        PageRequest.of(0, limit));
        if (keys.isEmpty()) {
            return groupMethodResult(DuplicateSearchMethod.EXACT, DuplicateMethodStatus.COMPLETED, List.of());
        }

        List<String> hashValues = keys.stream().map(ExactDuplicateGroupKeyProjection::hashValue).toList();
        Map<ExactGroupKey, List<ExactDuplicateGroupFileProjection>> filesByKey = exactDuplicateCandidateRepository.findGroupFiles(
                        actorUserId,
                        hashValues,
                        folderId,
                        mimeType)
                .stream()
                .collect(Collectors.groupingBy(
                        file -> new ExactGroupKey(file.algorithm(), file.hashValue()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<DuplicateGroupResponse> groups = keys.stream()
                .map(key -> exactGroup(key, filesByKey.getOrDefault(
                        new ExactGroupKey(key.algorithm(), key.hashValue()),
                        List.of())))
                .filter(group -> group.files().size() > 1)
                .toList();

        return groupMethodResult(DuplicateSearchMethod.EXACT, DuplicateMethodStatus.COMPLETED, groups);
    }

    private FileEntity loadOwnedSourceFile(UUID sourceFileId, UUID actorUserId) {
        FileEntity sourceFile = fileRepository.findEligibleById(sourceFileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + sourceFileId));

        if (!sourceFile.isOwnedBy(actorUserId)) {
            throw new AccessDeniedException("You do not have permission to run duplicate detection for this file.");
        }

        return sourceFile;
    }

    private List<DuplicateSearchMethod> normalizeMethods(Collection<DuplicateSearchMethod> requestedMethods) {
        if (requestedMethods == null || requestedMethods.isEmpty()) {
            return DEFAULT_METHODS;
        }

        Set<DuplicateSearchMethod> seen = EnumSet.noneOf(DuplicateSearchMethod.class);
        List<DuplicateSearchMethod> normalized = new ArrayList<>();
        for (DuplicateSearchMethod method : requestedMethods) {
            if (method != null && seen.add(method)) {
                normalized.add(method);
            }
        }

        return normalized.isEmpty() ? DEFAULT_METHODS : normalized;
    }

    private DuplicateMethodResultResponse methodResult(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateMatchResponse> matches,
            int pageSize) {
        return DuplicateMethodResultResponse.builder()
                .method(method)
                .status(status)
                .matches(matches)
                .pageSize(pageSize)
                .hasMore(false)
                .build();
    }

    private DuplicateMethodResultResponse pagedMethodResult(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateMatchResponse> fetchedMatches,
            int pageSize,
            SearchCursor nextCursor) {
        return pagedMethodResult(method, status, fetchedMatches, pageSize, nextCursor, false);
    }

    private DuplicateMethodResultResponse pagedMethodResult(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateMatchResponse> fetchedMatches,
            int pageSize,
            SearchCursor nextCursor,
            boolean forceHasMore) {
        boolean hasMore = fetchedMatches.size() > pageSize || forceHasMore;
        List<DuplicateMatchResponse> matches = hasMore
                ? fetchedMatches.subList(0, pageSize)
                : fetchedMatches;

        return DuplicateMethodResultResponse.builder()
                .method(method)
                .status(status)
                .matches(matches)
                .pageSize(pageSize)
                .hasMore(hasMore)
                .nextCursor(hasMore ? encodeCursor(nextCursor) : null)
                .build();
    }

    private DuplicateGroupMethodResultResponse groupMethodResult(
            DuplicateSearchMethod method,
            DuplicateMethodStatus status,
            List<DuplicateGroupResponse> groups) {
        return DuplicateGroupMethodResultResponse.builder()
                .method(method)
                .status(status)
                .groups(groups)
                .build();
    }

    private DuplicateMatchResponse exactMatch(UUID fileId, String algorithm) {
        return DuplicateMatchResponse.builder()
                .fileId(fileId)
                .confidence(DuplicateConfidence.EXACT)
                .score(1.0)
                .evidence(List.of(DuplicateEvidenceResponse.builder()
                        .type(DuplicateEvidenceType.CHECKSUM)
                        .score(1.0)
                        .details(Map.of("algorithm", algorithm))
                        .build()))
                .build();
    }

    private DuplicateMatchResponse phashMatch(UUID fileId, int distance) {
        double score = Math.max(0.0, 1.0 - distance / 64.0);

        return DuplicateMatchResponse.builder()
                .fileId(fileId)
                .confidence(DuplicateConfidence.NEAR_DUPLICATE)
                .score(score)
                .evidence(List.of(DuplicateEvidenceResponse.builder()
                        .type(DuplicateEvidenceType.IMAGE_PHASH)
                        .score(score)
                        .details(Map.of("distance", distance))
                        .build()))
                .build();
    }

    private DuplicateMatchResponse embeddingMatch(UUID fileId, double distance, DuplicateEvidenceType evidenceType) {
        double score = Math.max(0.0, 1.0 - distance / 2.0);

        return DuplicateMatchResponse.builder()
                .fileId(fileId)
                .confidence(DuplicateConfidence.NEAR_DUPLICATE)
                .score(score)
                .evidence(List.of(DuplicateEvidenceResponse.builder()
                        .type(evidenceType)
                        .score(score)
                        .details(Map.of("distance", distance))
                        .build()))
                .build();
    }

    private DuplicateMatchResponse audioMatch(UUID fileId) {
        return DuplicateMatchResponse.builder()
                .fileId(fileId)
                .confidence(DuplicateConfidence.EXACT)
                .score(1.0)
                .evidence(List.of(DuplicateEvidenceResponse.builder()
                        .type(DuplicateEvidenceType.AUDIO_FINGERPRINT)
                        .score(1.0)
                        .details(Map.of("match", "exact fingerprint"))
                        .build()))
                .build();
    }

    private DuplicateGroupResponse exactGroup(
            ExactDuplicateGroupKeyProjection key,
            List<ExactDuplicateGroupFileProjection> files) {
        List<DuplicateGroupFileResponse> summaries = files.stream()
                .map(file -> DuplicateGroupFileResponse.builder()
                        .fileId(file.fileId())
                        .name(file.name())
                        .mimeType(file.mimeType())
                        .size(file.size())
                        .build())
                .toList();

        return DuplicateGroupResponse.builder()
                .groupId(stableGroupId(DuplicateSearchMethod.EXACT.name(), key.algorithm().name(), key.hashValue()))
                .confidence(DuplicateConfidence.EXACT)
                .representativeFileId(representativeFileId(summaries))
                .files(summaries)
                .evidence(List.of(DuplicateGroupEvidenceResponse.builder()
                        .type(DuplicateEvidenceType.CHECKSUM)
                        .score(1.0)
                        .details(Map.of("algorithm", key.algorithm().name()))
                        .build()))
                .build();
    }

    private UUID representativeFileId(List<DuplicateGroupFileResponse> files) {
        return files.stream()
                .map(DuplicateGroupFileResponse::fileId)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean isImage(FileEntity file) {
        return mimeMatches(file, appProperties.getProcessableImageMimeTypes(), "image/");
    }

    private boolean isAudio(FileEntity file) {
        return mimeMatches(file, appProperties.getProcessableAudioMimeTypes(), "audio/");
    }

    private boolean mimeMatches(FileEntity file, Set<String> configuredTypes, String fallbackPrefix) {
        String mimeType = normalizeOptionalText(file.getMimeType());
        if (mimeType == null) {
            return false;
        }

        String lowerMimeType = mimeType.toLowerCase(Locale.ROOT);

        return configuredTypes.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(lowerMimeType::equals)
                || lowerMimeType.startsWith(fallbackPrefix);
    }

    private boolean allowsConfidence(DuplicateConfidence requestedMinimum, DuplicateConfidence actualConfidence) {
        return confidenceRank(actualConfidence) >= confidenceRank(requestedMinimum);
    }

    private boolean hasSummaryCompatibleFilters(UUID folderId, String mimeType) {
        return folderId == null && mimeType == null;
    }

    private int pageSize(DuplicateSearchPageRequest pageRequest, int defaultPageSize) {
        Integer requested = pageRequest == null ? null : pageRequest.pageSize();
        int pageSize = requested == null ? defaultPageSize : requested;

        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("Duplicate search pageSize must be between 1 and 1000.");
        }

        return pageSize;
    }

    private SearchCursor validateAndDecodeCursor(
            Collection<DuplicateSearchMethod> requestedMethods,
            List<DuplicateSearchMethod> methods,
            DuplicateSearchPageRequest pageRequest) {
        String rawCursor = pageRequest == null ? null : pageRequest.cursor();
        if (rawCursor == null || rawCursor.isBlank()) {
            return null;
        }

        long explicitMethodCount = requestedMethods == null
                ? 0
                : requestedMethods.stream().filter(Objects::nonNull).distinct().count();
        if (explicitMethodCount != 1 || methods.size() != 1) {
            throw new IllegalArgumentException("Duplicate search cursor requests must specify exactly one method.");
        }

        SearchCursor cursor = decodeCursor(rawCursor);
        if (cursor.method() != methods.getFirst()) {
            throw new IllegalArgumentException("Duplicate search cursor method does not match requested method.");
        }

        return cursor;
    }

    private SearchCursor methodCursor(SearchCursor cursor, DuplicateSearchMethod method) {
        if (cursor == null || cursor.method() != method) {
            return null;
        }

        return cursor;
    }

    private SearchCursor exactNextCursor(
            List<com.filemanager.api.duplicate.persistence.ExactDuplicateCandidateProjection> candidates,
            int pageSize) {
        if (candidates.size() <= pageSize) {
            return null;
        }

        var last = candidates.get(pageSize - 1);

        return new SearchCursor(DuplicateSearchMethod.EXACT, last.createdAt(), last.fileId(), null);
    }

    private SearchCursor phashNextCursor(
            List<com.filemanager.api.duplicate.persistence.PhashDuplicateCandidateProjection> candidates,
            int pageSize) {
        if (candidates.size() <= pageSize) {
            return null;
        }

        var last = candidates.get(pageSize - 1);

        return new SearchCursor(
                DuplicateSearchMethod.IMAGE_PHASH,
                last.getCreatedAt(),
                last.getFileId(),
                last.getDistance().doubleValue());
    }

    private SearchCursor embeddingNextCursor(
            List<com.filemanager.api.duplicate.persistence.EmbeddingDuplicateCandidateProjection> candidates,
            int pageSize,
            boolean forceHasMore) {
        if (candidates.size() <= pageSize && !forceHasMore) {
            return null;
        }

        var last = candidates.get(Math.clamp(pageSize, 1, candidates.size()) - 1);

        return new SearchCursor(
                DuplicateSearchMethod.IMAGE_EMBEDDING,
                last.getCreatedAt(),
                last.getFileId(),
                last.getDistance());
    }

    private SearchCursor audioNextCursor(
            List<com.filemanager.api.duplicate.persistence.AudioDuplicateCandidateProjection> candidates,
            int pageSize) {
        if (candidates.size() <= pageSize) {
            return null;
        }

        var last = candidates.get(pageSize - 1);

        return new SearchCursor(DuplicateSearchMethod.AUDIO_FINGERPRINT, last.createdAt(), last.fileId(), null);
    }

    private AdaptiveEmbeddingCandidates findAdaptiveEmbeddingCandidates(
            UUID actorUserId,
            UUID sourceFileId,
            String modelName,
            String modelVersion,
            int dimension,
            String sourceEmbedding,
            SearchCursor methodCursor,
            int pageSize) {
        int maxSearchWindow = Math.max(
                properties.getImageEmbedding().getSearchWindow(),
                properties.getImageEmbedding().getMaxSearchWindow());
        int searchWindow = Math.clamp(
                pageSize + 1,
                properties.getImageEmbedding().getSearchWindow(),
                maxSearchWindow);
        List<com.filemanager.api.duplicate.persistence.EmbeddingDuplicateCandidateProjection> candidates;

        while (true) {
            candidates = imageEmbeddingDuplicateCandidateRepository.findCandidates(
                    actorUserId,
                    sourceFileId,
                    modelName,
                    modelVersion,
                    dimension,
                    sourceEmbedding,
                    properties.getImageEmbedding().getMaxDistance(),
                    searchWindow,
                    methodCursor == null ? null : methodCursor.distance(),
                    methodCursor == null ? null : methodCursor.createdAt().toString(),
                    methodCursor == null ? null : methodCursor.fileId().toString(),
                    pageSize + 1);
            if (candidates.size() > pageSize || searchWindow >= maxSearchWindow) {
                break;
            }

            searchWindow = searchWindow > maxSearchWindow / 2 ? maxSearchWindow : searchWindow * 2;
        }

        boolean exhaustedBoundedWindowWithFullPage = candidates.size() == pageSize;

        return new AdaptiveEmbeddingCandidates(candidates, exhaustedBoundedWindowWithFullPage);
    }

    private String encodeCursor(SearchCursor cursor) {
        String rawCursor = cursor.method().name()
                + "|" + cursor.createdAt()
                + "|" + cursor.fileId()
                + "|" + (cursor.distance() == null ? "" : cursor.distance());

        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    private SearchCursor decodeCursor(String rawCursor) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid duplicate search cursor.");
            }

            DuplicateSearchMethod method = DuplicateSearchMethod.valueOf(parts[0]);
            OffsetDateTime createdAt = OffsetDateTime.parse(parts[1]);
            UUID fileId = UUID.fromString(parts[2]);
            Double distance = parts[3].isBlank() ? null : Double.parseDouble(parts[3]);

            return new SearchCursor(method, createdAt, fileId, distance);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid duplicate search cursor.", ex);
        }
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");

        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }

            builder.append(vector[index]);
        }

        return builder.append(']').toString();
    }

    private int confidenceRank(DuplicateConfidence confidence) {
        return switch (confidence) {
            case SIMILAR -> 1;
            case NEAR_DUPLICATE -> 2;
            case EXACT -> 3;
        };
    }

    private void recordMethodMetrics(DuplicateSearchMethod method, DuplicateMethodStatus status, int matchesReturned) {
        if (status == DuplicateMethodStatus.COMPLETED) {
            fileManagerMetrics.recordDuplicateSearchMethodCompleted(method.name());
        } else if (status == DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY) {
            fileManagerMetrics.recordDuplicateSearchMethodNotReady(method.name());
        } else if (status == DuplicateMethodStatus.DISABLED_BY_CONFIG) {
            fileManagerMetrics.recordDuplicateSearchMethodDisabled(method.name());
        }

        fileManagerMetrics.recordDuplicateMatchesReturned(method.name(), matchesReturned);
    }

    private String stableGroupId(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            for (String part : parts) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private record ExactGroupKey(FingerprintAlgorithm algorithm, String hashValue) {
    }

    private record SearchCursor(
            DuplicateSearchMethod method,
            OffsetDateTime createdAt,
            UUID fileId,
            Double distance) {
    }

    private record AdaptiveEmbeddingCandidates(
            List<com.filemanager.api.duplicate.persistence.EmbeddingDuplicateCandidateProjection> matches,
            boolean forceHasMore) {
        java.util.stream.Stream<com.filemanager.api.duplicate.persistence.EmbeddingDuplicateCandidateProjection> stream() {
            return matches.stream();
        }
    }
}
