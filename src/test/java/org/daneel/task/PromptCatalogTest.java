package org.daneel.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PromptCatalogTest {

    @TempDir
    Path tempDir;

    private PromptCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new PromptCatalog(tempDir.toString());
    }

    @Test
    void list_returnsSummaryForEachMdFile() throws Exception {
        Files.writeString(tempDir.resolve("hello.md"),
                "---\nsummary: Says hello.\n---\nSpeak hello.");
        var result = catalog.list();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().promptFile()).isEqualTo("hello.md");
        assertThat(result.getFirst().summary()).isEqualTo("Says hello.");
    }

    @Test
    void list_withNoFrontmatter_returnsEmptySummary() throws Exception {
        Files.writeString(tempDir.resolve("bare.md"), "Just body, no frontmatter.");
        var result = catalog.list();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().summary()).isEmpty();
    }

    @Test
    void list_whenDirIsEmpty_returnsEmptyList() {
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void list_ignoresNonMdFiles() throws Exception {
        Files.writeString(tempDir.resolve("tasks.yml"), "tasks: []");
        Files.writeString(tempDir.resolve("note.txt"), "ignored");
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void exists_whenFilePresent_returnsTrue() throws Exception {
        Files.writeString(tempDir.resolve("hello.md"), "content");
        assertThat(catalog.exists("hello.md")).isTrue();
    }

    @Test
    void exists_whenFileAbsent_returnsFalse() {
        assertThat(catalog.exists("missing.md")).isFalse();
    }
}
