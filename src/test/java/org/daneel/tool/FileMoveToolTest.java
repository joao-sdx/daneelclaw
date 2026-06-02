package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMoveToolTest {

  @TempDir Path tempDir;

  private FileMoveTool tool;

  @BeforeEach
  void setUp() {
    tool = new FileMoveTool(new SandboxFileSystem(tempDir.toString()));
  }

  @Test
  void execute_movesFile() throws Exception {
    Files.writeString(tempDir.resolve("old.txt"), "content");
    tool.execute(Map.of("source", "old.txt", "destination", "new.txt"));
    assertThat(Files.exists(tempDir.resolve("old.txt"))).isFalse();
    assertThat(Files.readString(tempDir.resolve("new.txt"), StandardCharsets.UTF_8))
        .isEqualTo("content");
  }

  @Test
  void execute_movesToSubdirectory() throws Exception {
    Files.writeString(tempDir.resolve("note.txt"), "hi");
    tool.execute(Map.of("source", "note.txt", "destination", "archive/note.txt"));
    assertThat(Files.exists(tempDir.resolve("note.txt"))).isFalse();
    assertThat(Files.exists(tempDir.resolve("archive/note.txt"))).isTrue();
  }

  @Test
  void execute_createsDestinationParents() throws Exception {
    Files.writeString(tempDir.resolve("f.txt"), "x");
    tool.execute(Map.of("source", "f.txt", "destination", "a/b/c/f.txt"));
    assertThat(Files.exists(tempDir.resolve("a/b/c/f.txt"))).isTrue();
  }

  @Test
  void execute_sourceNotFound_returnsError() {
    assertThat(tool.execute(Map.of("source", "ghost.txt", "destination", "dest.txt")))
        .startsWith("Error:");
  }

  @Test
  void execute_missingSource_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("source", null);
    params.put("destination", "dest.txt");
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_missingDestination_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("source", "src.txt");
    params.put("destination", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_traversalInSource_returnsError() throws Exception {
    assertThat(tool.execute(Map.of("source", "../escape.txt", "destination", "dest.txt")))
        .startsWith("Error:");
  }

  @Test
  void execute_traversalInDestination_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("f.txt"), "x");
    assertThat(tool.execute(Map.of("source", "f.txt", "destination", "../escape.txt")))
        .startsWith("Error:");
  }
}
