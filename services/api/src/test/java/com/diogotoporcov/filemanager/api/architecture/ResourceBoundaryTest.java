package com.diogotoporcov.filemanager.api.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceBoundaryTest {
    private static final Set<String> ALLOWED_SHARING_PACKAGES = Set.of(
            "application",
            "domain",
            "persistence",
            "web");

    @Test
    void sharingModuleUsesModuleFirstPackageShape() throws Exception {
        Path sharingDirectory = sourceDirectory().resolve("sharing");

        try (Stream<Path> paths = Files.list(sharingDirectory)) {
            Set<String> packageDirectories = paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());

            assertThat(packageDirectories).isEqualTo(ALLOWED_SHARING_PACKAGES);
        }
    }

    @Test
    void sharingModuleDoesNotUseLegacyRootPackages() throws Exception {
        Path sharingDirectory = sourceDirectory().resolve("sharing");
        Set<String> legacyPackages = Set.of("controller", "dto", "entity", "repository", "service", "mapper");

        try (Stream<Path> paths = Files.list(sharingDirectory)) {
            assertThat(paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet()))
                    .doesNotContainAnyElementsOf(legacyPackages);
        }
    }

    private Path sourceDirectory() {
        return Path.of(System.getProperty("user.dir"), "src/main/java/com/diogotoporcov/filemanager/api");
    }
}

