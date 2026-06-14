package com.filemanager.api.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRunContextTest {
    @TempDir
    private Path directory;

    @Test
    void oneInvocationHasOneRunId() throws Exception {
        BenchmarkOptions options = options("run-one", 10_000);

        BenchmarkRunContext context = BenchmarkRunContext.create(options);

        assertThat(context.runId()).isEqualTo("run-one");
        assertThat(context.scale()).isEqualTo("10k");
        assertThat(context.scaleDir()).isEqualTo(directory.resolve("reports/run-one/10k"));
    }

    @Test
    void benchmarkAllCanShareRunIdAcrossScales() throws Exception {
        BenchmarkRunContext ten = BenchmarkRunContext.create(options("shared-run", 10_000));
        BenchmarkRunContext hundred = BenchmarkRunContext.create(options("shared-run", 100_000));
        BenchmarkRunContext million = BenchmarkRunContext.create(options("shared-run", 1_000_000));

        assertThat(ten.runId()).isEqualTo(hundred.runId()).isEqualTo(million.runId());
        assertThat(ten.scaleDir()).isNotEqualTo(hundred.scaleDir()).isNotEqualTo(million.scaleDir());
    }

    @Test
    void differentInvocationsUseDifferentRunIds() throws Exception {
        BenchmarkRunContext first = BenchmarkRunContext.create(options("first-run", 10_000));
        BenchmarkRunContext second = BenchmarkRunContext.create(options("second-run", 10_000));

        assertThat(first.runId()).isNotEqualTo(second.runId());
        assertThat(first.scaleDir()).isNotEqualTo(second.scaleDir());
    }

    @Test
    void existingScaleDirectoryIsFreshenedWithoutColliding() throws Exception {
        BenchmarkOptions options = options("fresh-run", 10_000);
        Path stale = directory.resolve("reports/fresh-run/10k/stale.txt");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "stale");

        BenchmarkRunContext context = BenchmarkRunContext.create(options);

        assertThat(context.scaleDir()).exists();
        assertThat(stale).doesNotExist();
        assertThatCode(() -> Files.createFile(context.scaleDir().resolve("new.txt"))).doesNotThrowAnyException();
    }

    private BenchmarkOptions options(String runId, int records) {
        return new BenchmarkOptions(
                records,
                20260611L,
                1,
                1,
                "1",
                "default",
                "default",
                new BenchmarkProfileSupport.BaselineMetadata("", "", "", ""),
                runId,
                "metrics",
                directory.resolve("reports"),
                directory.resolve("results"),
                "python",
                "FALLBACK",
                "python",
                Map.of());
    }
}
