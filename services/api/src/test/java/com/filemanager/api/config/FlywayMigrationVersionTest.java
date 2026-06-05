package com.filemanager.api.config;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V([^_]+)__.+\\.sql$");

    @Test
    void migrationVersions_AreUnique() throws Exception {
        List<String> versions = migrationVersions();

        Map<String, Long> versionCounts = versions.stream()
                .collect(Collectors.groupingBy(version -> version, Collectors.counting()));

        assertThat(versionCounts)
                .allSatisfy((version, count) -> assertThat(count)
                        .as("Flyway migration version " + version)
                        .isEqualTo(1));
    }

    private List<String> migrationVersions() throws Exception {
        Path migrationDirectory = migrationDirectory();

        try (Stream<Path> paths = Files.list(migrationDirectory)) {
            return paths
                    .map(path -> path.getFileName().toString())
                    .map(VERSION_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .toList();
        }
    }

    private Path migrationDirectory() throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("db/migration");
        assertThat(resource).isNotNull();

        return Path.of(resource.toURI());
    }
}
