package com.filemanager.api.architecture;

import com.filemanager.api.dto.BoundedOffsetPageRequest;
import com.filemanager.api.dto.BoundedPageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceBoundaryTest {

    private static final Path API_SOURCE = resolveApiSource();

    @Test
    void getCollectionEndpoints_ShouldNotReturnRawLists() throws ClassNotFoundException {
        List<Class<?>> controllers = List.of(
                Class.forName("com.filemanager.api.controller.FileController"),
                Class.forName("com.filemanager.api.controller.DuplicateCandidateController"),
                Class.forName("com.filemanager.api.controller.ProcessingStatusController")
        );

        List<String> violations = controllers.stream()
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .filter(this::returnsRawList)
                .map(method -> method.getDeclaringClass().getSimpleName() + "#" + method.getName())
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void repositories_ShouldNotExposeUnpagedFindAllByListMethods() {
        List<String> violations = findJavaFiles(API_SOURCE.resolve("repository")).stream()
                .flatMap(this::repositoryMethodViolations)
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void duplicateCandidateAdapter_ShouldUsePageableSpecificationQueries() throws Exception {
        String source = Files.readString(API_SOURCE.resolve("adapter/JpaDuplicateCandidateSearchAdapter.java"));

        assertThat(source).contains("findAll(spec, pageable)");
        assertThat(source).doesNotContain("findAll(spec);");
    }

    @Test
    void boundedPageRequests_ShouldRejectOversizedClientRequests() {
        assertThatThrownBy(() -> BoundedPageRequest.of(BoundedPageRequest.MAX_SIZE + 1, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BoundedOffsetPageRequest.of(0, BoundedOffsetPageRequest.MAX_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private boolean returnsRawList(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getRawType().equals(List.class);
        }
        return method.getReturnType().equals(List.class);
    }

    private List<Path> findJavaFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to inspect source files", ex);
        }
    }

    private Stream<String> repositoryMethodViolations(Path sourceFile) {
        try {
            String source = Files.readString(sourceFile);
            if (!source.contains("List<") || !source.contains("findAllBy")) {
                return Stream.empty();
            }
            return source.lines()
                    .filter(line -> line.contains("List<") && line.contains("findAllBy"))
                    .filter(line -> !line.contains(Pageable.class.getSimpleName()))
                    .map(line -> sourceFile.getFileName() + ": " + line.trim());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to inspect " + sourceFile, ex);
        }
    }

    private static Path resolveApiSource() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path moduleRelative = workingDirectory.resolve("src/main/java/com/filemanager/api");
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return workingDirectory.resolve("services/api/src/main/java/com/filemanager/api");
    }
}
