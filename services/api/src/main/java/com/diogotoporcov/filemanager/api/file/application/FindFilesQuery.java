package com.diogotoporcov.filemanager.api.file.application;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
public class FindFilesQuery {
    private UUID folderId;
    private UUID tagId;
    private String createdAtFrom;
    private String createdAtTo;
    private String updatedAtFrom;
    private String updatedAtTo;
    private Long sizeMin;
    private Long sizeMax;
    private List<String> mimeType;
    private String sort;
    private Integer size;
    private Integer limit;
    private String cursor;
}
