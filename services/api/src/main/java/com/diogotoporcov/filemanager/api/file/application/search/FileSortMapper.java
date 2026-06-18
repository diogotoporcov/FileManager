package com.diogotoporcov.filemanager.api.file.application.search;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FileSortMapper {
    private static final SortSpec DEFAULT_SORT = new SortSpec("createdAt", Sort.Direction.DESC);

    private static final Map<String, String> ALLOWED_SORTS = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "name", "name",
            "size", "size"
    );

    public SortSpec parse(String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return DEFAULT_SORT;
        }

        String[] parts = rawSort.split(",", -1);
        if (parts.length != 2) {
            throw new SearchValidationException("Sort must use the format field,direction");
        }

        String field = parts[0].trim();
        if (!ALLOWED_SORTS.containsKey(field)) {
            throw new SearchValidationException("Unsupported sort field: " + field);
        }

        return new SortSpec(field, FileSortDirections.parse(parts[1]));
    }

    public Sort toSort(SortSpec sortSpec) {
        Sort.Direction direction = sortSpec.direction();

        return Sort.by(direction, entityProperty(sortSpec.field()))
                .and(Sort.by(direction, "id"));
    }

    public String entityProperty(String publicField) {
        String entityProperty = ALLOWED_SORTS.get(publicField);
        if (entityProperty == null) {
            throw new SearchValidationException("Unsupported sort field: " + publicField);
        }

        return entityProperty;
    }
}
