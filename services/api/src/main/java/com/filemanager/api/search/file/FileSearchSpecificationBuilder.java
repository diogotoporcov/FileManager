package com.filemanager.api.search.file;

import com.filemanager.api.entity.FileEntity;
import com.filemanager.api.entity.FileTagEntity;
import com.filemanager.api.search.SearchValidationException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FileSearchSpecificationBuilder {
    public Specification<FileEntity> build(FileSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (criteria.ownerUserId() == null) {
                throw new SearchValidationException("Exactly one owner scope is required");
            }
            predicates.add(cb.equal(root.get("ownerUser").get("id"), criteria.ownerUserId()));

            if (criteria.folderId() != null) {
                predicates.add(cb.equal(root.get("folder").get("id"), criteria.folderId()));
            }

            if (criteria.tagId() != null) {
                predicates.add(tagAssignmentExists(root, query, cb, criteria.tagId()));
            }

            addDateTimeRange(predicates, cb, root.get("createdAt"), criteria.createdAt());
            addDateTimeRange(predicates, cb, root.get("updatedAt"), criteria.updatedAt());
            addLongRange(predicates, cb, root.get("size"), criteria.size());

            if (!criteria.mimeTypes().isEmpty()) {
                predicates.add(root.get("mimeType").in(criteria.mimeTypes()));
            }

            if (criteria.cursor() != null) {
                predicates.add(cursorPredicate(root, cb, criteria));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate tagAssignmentExists(
            Root<FileEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            UUID tagId) {
        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<FileTagEntity> assignment = subquery.from(FileTagEntity.class);
        subquery.select(assignment.get("file").get("id"));
        subquery.where(
                cb.equal(assignment.get("file").get("id"), root.get("id")),
                cb.equal(assignment.get("tag").get("id"), tagId),
                cb.isNull(assignment.get("tag").get("deletedAt")));
        return cb.exists(subquery);
    }

    private void addDateTimeRange(List<Predicate> predicates, CriteriaBuilder cb, Path<OffsetDateTime> path, DateTimeRange range) {
        if (!range.hasBounds()) {
            return;
        }
        if (range.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, range.from()));
        }
        if (range.to() != null) {
            predicates.add(cb.lessThan(path, range.to()));
        }
    }

    private void addLongRange(List<Predicate> predicates, CriteriaBuilder cb, Path<Long> path, LongRange range) {
        if (!range.hasBounds()) {
            return;
        }
        if (range.min() != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, range.min()));
        }
        if (range.max() != null) {
            predicates.add(cb.lessThanOrEqualTo(path, range.max()));
        }
    }

    private Predicate cursorPredicate(Root<FileEntity> root, CriteriaBuilder cb, FileSearchCriteria criteria) {
        FileSearchCursor cursor = criteria.cursor();
        boolean descending = cursor.direction() == Sort.Direction.DESC;

        return switch (cursor.sortField()) {
            case "createdAt" -> compareWithIdTieBreaker(cb, root.get("createdAt"), OffsetDateTime.parse(cursor.value()), root.get("id"), cursor.id(), descending);
            case "updatedAt" -> compareWithIdTieBreaker(cb, root.get("updatedAt"), OffsetDateTime.parse(cursor.value()), root.get("id"), cursor.id(), descending);
            case "name" -> compareWithIdTieBreaker(cb, root.get("name"), cursor.value(), root.get("id"), cursor.id(), descending);
            case "size" -> compareWithIdTieBreaker(cb, root.get("size"), Long.parseLong(cursor.value()), root.get("id"), cursor.id(), descending);
            default -> throw new SearchValidationException("Unsupported sort field: " + cursor.sortField());
        };
    }

    private <T extends Comparable<? super T>> Predicate compareWithIdTieBreaker(
            CriteriaBuilder cb,
            Path<T> sortPath,
            T cursorValue,
            Path<UUID> idPath,
            UUID cursorId,
            boolean descending) {
        Predicate valuePastCursor = descending
                ? cb.lessThan(sortPath, cursorValue)
                : cb.greaterThan(sortPath, cursorValue);
        Predicate idPastCursor = descending
                ? cb.lessThan(idPath, cursorId)
                : cb.greaterThan(idPath, cursorId);
        return cb.or(valuePastCursor, cb.and(cb.equal(sortPath, cursorValue), idPastCursor));
    }
}
