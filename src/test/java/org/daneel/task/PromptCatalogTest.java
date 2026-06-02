package org.daneel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptCatalogTest {

  @TempDir Path tempDir;

  private PromptCatalog catalog;

  @BeforeEach
  void setUp() {
    catalog = new PromptCatalog(tempDir.toString());
  }

  @Test
  void list_returnsSummaryForEachMdFile() throws Exception {
    Files.writeString(tempDir.resolve("hello.md"), "---\nsummary: Says hello.\n---\nSpeak hello.");
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

  @Test
  void isAdhoc_trueForTimestampedPattern() {
    assertThat(catalog.isAdhoc("p1717320000000.md")).isTrue();
    assertThat(catalog.isAdhoc("p0.md")).isTrue();
  }

  @Test
  void isAdhoc_falseForNamedPrompts() {
    assertThat(catalog.isAdhoc("hello.md")).isFalse();
    assertThat(catalog.isAdhoc("prompt.md")).isFalse();
    assertThat(catalog.isAdhoc("p-custom.md")).isFalse();
    assertThat(catalog.isAdhoc(null)).isFalse();
  }

  @Test
  void delete_removesExistingFile() throws Exception {
    var file = tempDir.resolve("p1717000000000.md");
    Files.writeString(file, "body");
    catalog.delete("p1717000000000.md");
    assertThat(Files.exists(file)).isFalse();
  }

  @Test
  void delete_noErrorWhenFileAbsent() {
    catalog.delete("p9999999999999.md"); // should not throw
  }
}
