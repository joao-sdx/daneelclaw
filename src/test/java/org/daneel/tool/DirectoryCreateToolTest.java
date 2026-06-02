package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryCreateToolTest {

  @TempDir Path tempDir;

  private DirectoryCreateTool tool;

  @BeforeEach
  void setUp() {
    tool = new DirectoryCreateTool(new SandboxFileSystem(tempDir.toString()));
  }

  @Test
  void execute_createsDirectory() {
    tool.execute(Map.of("path", "mydir"));
    assertThat(Files.isDirectory(tempDir.resolve("mydir"))).isTrue();
  }

  @Test
  void execute_createsNestedDirectories() {
    tool.execute(Map.of("path", "a/b/c"));
    assertThat(Files.isDirectory(tempDir.resolve("a/b/c"))).isTrue();
  }

  @Test
  void execute_idempotent_alreadyExists() {
    tool.execute(Map.of("path", "existing"));
    var result = tool.execute(Map.of("path", "existing"));
    // second call should not error
    assertThat(result).doesNotStartWith("Error:");
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
  void execute_returnsConfirmation() {
    var result = tool.execute(Map.of("path", "newdir"));
    assertThat(result).contains("newdir");
  }
}
