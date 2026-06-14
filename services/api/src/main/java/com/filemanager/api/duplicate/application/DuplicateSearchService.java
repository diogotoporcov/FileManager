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
import java.util.ArrayList;
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
        long started = System.nanoTime();
        String status = "success";
        try {
            FileEntity sourceFile = loadOwnedSourceFile(sourceFileId, actorUserId);
            List<DuplicateSearchMethod> methods = normalizeMethods(requestedMethods);
            fileManagerMetrics.recordDuplicateSearchRequested();

            List<DuplicateMethodResultResponse> methodResponses = methods.stream()
                    .map(method -> searchByMethod(method, sourceFile, actorUserId))
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
            UUID actorUserId) {
        DuplicateMethodResultResponse response = switch (method) {
            case EXACT -> searchExact(sourceFile, actorUserId);
            case IMAGE_PHASH -> searchImagePhash(sourceFile, actorUserId);
            case IMAGE_EMBEDDING -> searchImageEmbedding(sourceFile, actorUserId);
            case AUDIO_FINGERPRINT -> searchAudioFingerprint(sourceFile, actorUserId);
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

    private DuplicateMethodResultResponse searchExact(FileEntity sourceFile, UUID actorUserId) {
        if (!properties.getExact().isEnabled()) {
            return methodResult(DuplicateSearchMethod.EXACT, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of());
        }

        return fileFingerprintRepository.findByFileIdAndAlgorithm(sourceFile.getId(), EXACT_ALGORITHM)
                .map(sourceFingerprint -> {
                    List<DuplicateMatchResponse> matches = exactDuplicateCandidateRepository.findCandidates(
                                    actorUserId,
                                    sourceFile.getId(),
                                    sourceFingerprint.getAlgorithm(),
                                    sourceFingerprint.getHashValue(),
                                    PageRequest.of(0, properties.getExact().getMaxCandidates()))
                            .stream()
                            .map(candidate -> exactMatch(candidate.fileId(), candidate.algorithm().name()))
                            .toList();
                    return methodResult(DuplicateSearchMethod.EXACT, DuplicateMethodStatus.COMPLETED, matches);
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.EXACT,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of()));
    }

    private DuplicateMethodResultResponse searchImagePhash(FileEntity sourceFile, UUID actorUserId) {
        if (!properties.getImagePhash().isEnabled()) {
            return methodResult(DuplicateSearchMethod.IMAGE_PHASH, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of());
        }

        if (!isImage(sourceFile)) {
            return methodResult(
                    DuplicateSearchMethod.IMAGE_PHASH,
                    DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE,
                    List.of());
        }

        return imageFingerprintRepository.findByFileId(sourceFile.getId())
                .map(sourceFingerprint -> {
                    List<DuplicateMatchResponse> matches = imagePhashDuplicateCandidateRepository.findCandidates(
                                    actorUserId,
                                    sourceFile.getId(),
                                    sourceFingerprint.getPhash(),
                                    properties.getImagePhash().getMaxDistance(),
                                    properties.getImagePhash().getMaxCandidates())
                            .stream()
                            .map(candidate -> phashMatch(candidate.getFileId(), candidate.getDistance()))
                            .toList();
                    return methodResult(DuplicateSearchMethod.IMAGE_PHASH, DuplicateMethodStatus.COMPLETED, matches);
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.IMAGE_PHASH,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of()));
    }

    private DuplicateMethodResultResponse searchImageEmbedding(FileEntity sourceFile, UUID actorUserId) {
        if (!properties.getImageEmbedding().isEnabled()) {
            return methodResult(DuplicateSearchMethod.IMAGE_EMBEDDING, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of());
        }

        if (!isImage(sourceFile)) {
            return methodResult(
                    DuplicateSearchMethod.IMAGE_EMBEDDING,
                    DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE,
                    List.of());
        }

        AppProperties.Embedding embeddingProperties = appProperties.getEmbedding();
        return fileEmbeddingRepository.findByFileIdAndModelNameAndModelVersion(
                        sourceFile.getId(),
                        embeddingProperties.getModelName(),
                        embeddingProperties.getModelVersion())
                .filter(sourceEmbedding -> Objects.equals(sourceEmbedding.getDimension(), embeddingProperties.getDimension()))
                .map(sourceEmbedding -> {
                    List<DuplicateMatchResponse> matches = imageEmbeddingDuplicateCandidateRepository.findCandidates(
                                    actorUserId,
                                    sourceFile.getId(),
                                    sourceEmbedding.getModelName(),
                                    sourceEmbedding.getModelVersion(),
                                    sourceEmbedding.getDimension(),
                                    properties.getImageEmbedding().getMaxDistance(),
                                    properties.getImageEmbedding().getMaxCandidates())
                            .stream()
                            .map(candidate -> embeddingMatch(
                                    candidate.getFileId(),
                                    candidate.getDistance(),
                                    DuplicateEvidenceType.IMAGE_EMBEDDING))
                            .toList();
                    return methodResult(DuplicateSearchMethod.IMAGE_EMBEDDING, DuplicateMethodStatus.COMPLETED, matches);
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.IMAGE_EMBEDDING,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of()));
    }

    private DuplicateMethodResultResponse searchAudioFingerprint(FileEntity sourceFile, UUID actorUserId) {
        if (!properties.getAudioFingerprint().isEnabled()) {
            return methodResult(DuplicateSearchMethod.AUDIO_FINGERPRINT, DuplicateMethodStatus.DISABLED_BY_CONFIG, List.of());
        }

        if (!isAudio(sourceFile)) {
            return methodResult(
                    DuplicateSearchMethod.AUDIO_FINGERPRINT,
                    DuplicateMethodStatus.UNSUPPORTED_FOR_FILE_TYPE,
                    List.of());
        }

        return audioFingerprintRepository.findByFileId(sourceFile.getId())
                .map(sourceFingerprint -> {
                    List<DuplicateMatchResponse> matches = audioDuplicateCandidateRepository.findCandidates(
                                    actorUserId,
                                    sourceFile.getId(),
                                    sourceFingerprint.getFingerprintAlgorithm(),
                                    sourceFingerprint.getFingerprintVersion(),
                                    sourceFingerprint.getFingerprintHash(),
                                    PageRequest.of(0, properties.getAudioFingerprint().getMaxCandidates()))
                            .stream()
                            .map(candidate -> audioMatch(candidate.fileId()))
                            .toList();
                    return methodResult(DuplicateSearchMethod.AUDIO_FINGERPRINT, DuplicateMethodStatus.COMPLETED, matches);
                })
                .orElseGet(() -> methodResult(
                        DuplicateSearchMethod.AUDIO_FINGERPRINT,
                        DuplicateMethodStatus.SOURCE_FINGERPRINT_NOT_READY,
                        List.of()));
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
            List<DuplicateMatchResponse> matches) {
        return DuplicateMethodResultResponse.builder()
                .method(method)
                .status(status)
                .matches(matches)
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
}
