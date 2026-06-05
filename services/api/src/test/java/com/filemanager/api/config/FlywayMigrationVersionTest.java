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

    @Test
    void sharingConstraintMigration_RejectsInertGrantPermissionsAndSplitsFolderUniqueness() throws Exception {
        String migration = Files.readString(migrationDirectory().resolve("V7__Harden_Sharing_Constraints.sql"));

        assertThat(migration).contains(
                "CHECK (permission IN ('FILE_VIEW', 'FILE_MODIFY', 'FILE_DELETE'))",
                "CHECK (permission IN ('FOLDER_VIEW', 'FOLDER_CREATE', 'FOLDER_RENAME', 'FOLDER_DELETE', 'FOLDER_UPLOAD_FILE'))",
                "ux_folders_owner_user_active_root_name",
                "WHERE parent_folder_id IS NULL AND deleted_at IS NULL",
                "ux_folders_parent_active_child_name",
                "WHERE parent_folder_id IS NOT NULL AND deleted_at IS NULL",
                "file_grants_no_self_grant",
                "folder_grants_no_self_grant",
                "CHECK (grantee_user_id <> created_by_user_id)");
        assertThat(migration).doesNotContain("FILE_SHARE", "FOLDER_MANAGE_PERMISSIONS");
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
