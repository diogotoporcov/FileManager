package com.filemanager.api.search.file;

import com.filemanager.api.search.AllowedSort;
import com.filemanager.api.search.SearchValidationException;
import com.filemanager.api.search.SortDirection;
import com.filemanager.api.search.SortSpec;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FileSortMapper {
    private static final SortSpec DEFAULT_SORT = new SortSpec("createdAt", SortDirection.DESC);

    private static final Map<String, AllowedSort> ALLOWED_SORTS = Map.of(
            "createdAt", new AllowedSort("createdAt", "createdAt"),
            "updatedAt", new AllowedSort("updatedAt", "updatedAt"),
            "name", new AllowedSort("name", "name"),
            "size", new AllowedSort("size", "size")
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

        return new SortSpec(field, SortDirection.parse(parts[1]));
    }

    public Sort toSort(SortSpec sortSpec) {
        Sort.Direction direction = sortSpec.direction() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, entityProperty(sortSpec.field()))
                .and(Sort.by(direction, "id"));
    }

    public String entityProperty(String publicField) {
        AllowedSort allowedSort = ALLOWED_SORTS.get(publicField);
        if (allowedSort == null) {
            throw new SearchValidationException("Unsupported sort field: " + publicField);
        }
        return allowedSort.entityProperty();
    }
}
