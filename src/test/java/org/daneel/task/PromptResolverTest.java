package org.daneel.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptResolverTest {

    @TempDir
    Path tempDir;

    private PromptResolver resolver;
    private PlannedTask task;

    @BeforeEach
    void setUp() {
        resolver = new PromptResolver(tempDir.toString());
        task = new PlannedTask("t1", "Test", "task-1.md",
                Instant.parse("2026-06-01T14:00:00Z"), 60, true);
    }

    @Test
    void resolve_substitutesTriggerTimeGmt() throws Exception {
        Files.writeString(tempDir.resolve("task-1.md"), "Triggered at {trigger_time_gmt}");
        var result = resolver.resolve(task,
                Instant.parse("2026-06-01T14:00:00Z"),
                Instant.parse("2026-06-01T14:00:03Z"));
        assertThat(result).contains("2026-06-01T14:00:00Z");
        assertThat(result).doesNotContain("{trigger_time_gmt}");
    }

    @Test
    void resolve_substitutesCurrentTimeGmt() throws Exception {
        Files.writeString(tempDir.resolve("task-1.md"), "Now: {current_time_gmt}");
        var result = resolver.resolve(task,
                Instant.parse("2026-06-01T14:00:00Z"),
                Instant.parse("2026-06-01T14:00:05Z"));
        assertThat(result).contains("2026-06-01T14:00:05Z");
        assertThat(result).doesNotContain("{current_time_gmt}");
    }

    @Test
    void resolve_substitutesLocalTimePlaceholders() throws Exception {
        Files.writeString(tempDir.resolve("task-1.md"),
                "{trigger_time_local} and {current_time_local}");
        var result = resolver.resolve(task,
                Instant.parse("2026-06-01T14:00:00Z"),
                Instant.parse("2026-06-01T14:00:03Z"));
        assertThat(result).doesNotContain("{trigger_time_local}");
        assertThat(result).doesNotContain("{current_time_local}");
    }

    @Test
    void resolve_noPlaceholders_returnsContentVerbatim() throws Exception {
        Files.writeString(tempDir.resolve("task-1.md"), "Hello world");
        var result = resolver.resolve(task, Instant.now(), Instant.now());
        assertThat(result).isEqualTo("Hello world");
    }

    @Test
    void resolve_throwsUncheckedIoExceptionWhenFileNotFound() {
        assertThatThrownBy(() -> resolver.resolve(task, Instant.now(), Instant.now()))
                .isInstanceOf(UncheckedIOException.class);
    }
}
