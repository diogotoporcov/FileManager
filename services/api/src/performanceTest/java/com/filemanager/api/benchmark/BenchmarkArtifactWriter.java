package com.filemanager.api.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class BenchmarkArtifactWriter {
    private final ObjectMapper objectMapper;

    BenchmarkArtifactWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void writeJson(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent());

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    void writeText(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());

        Files.writeString(path, text, StandardCharsets.UTF_8);
    }
}
