package org.daneel.tool.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDeleteToolTest {

  @TempDir Path tempDir;

  private FileDeleteTool tool;
  private SandboxFileSystem sandbox;

  @BeforeEach
  void setUp() {
    sandbox = new SandboxFileSystem(tempDir.toString());
    tool = new FileDeleteTool(sandbox);
  }

  @Test
  void execute_deletesFile() throws Exception {
    Files.writeString(tempDir.resolve("to-delete.txt"), "bye");
    tool.execute(Map.of("path", "to-delete.txt"));
    assertThat(Files.exists(tempDir.resolve("to-delete.txt"))).isFalse();
  }

  @Test
  void execute_deletesDirectoryRecursively() throws Exception {
    var sub = tempDir.resolve("sub");
    Files.createDirectories(sub);
    Files.writeString(sub.resolve("a.txt"), "a");
    Files.writeString(sub.resolve("b.txt"), "b");
    tool.execute(Map.of("path", "sub"));
    assertThat(Files.exists(sub)).isFalse();
  }

  @Test
  void execute_refusesToDeleteRoot() {
    var result = tool.execute(Map.of("path", ""));
    // empty path resolves to root — must be refused
    assertThat(result).startsWith("Error:");
  }

  @Test
  void execute_fileNotFound_returnsError() {
    assertThat(tool.execute(Map.of("path", "ghost.txt"))).startsWith("Error:");
  }

  @Test
  void execute_missingPath_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("path", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_traversalAttempt_returnsError() {
    assertThat(tool.execute(Map.of("path", "../escape"))).startsWith("Error:");
  }

  @Test
  void execute_returnsConfirmation() throws Exception {
    Files.writeString(tempDir.resolve("f.txt"), "x");
    var result = tool.execute(Map.of("path", "f.txt"));
    assertThat(result).contains("f.txt");
  }
}
