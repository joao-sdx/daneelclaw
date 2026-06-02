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

class FileWriteToolTest {

  @TempDir Path tempDir;

  private FileWriteTool tool;

  @BeforeEach
  void setUp() {
    tool = new FileWriteTool(new SandboxFileSystem(tempDir.toString()));
  }

  @Test
  void execute_writesNewFile() throws Exception {
    tool.execute(Map.of("path", "notes.txt", "content", "hello"));
    assertThat(Files.readString(tempDir.resolve("notes.txt"))).isEqualTo("hello");
  }

  @Test
  void execute_overwritesExistingFile() throws Exception {
    Files.writeString(tempDir.resolve("notes.txt"), "old content");
    tool.execute(Map.of("path", "notes.txt", "content", "new content"));
    assertThat(Files.readString(tempDir.resolve("notes.txt"))).isEqualTo("new content");
  }

  @Test
  void execute_createsParentDirectories() throws Exception {
    tool.execute(Map.of("path", "a/b/c.txt", "content", "deep"));
    assertThat(Files.readString(tempDir.resolve("a/b/c.txt"))).isEqualTo("deep");
  }

  @Test
  void execute_returnsByteCount() {
    var result = tool.execute(Map.of("path", "f.txt", "content", "hi"));
    assertThat(result).contains("2 bytes");
  }

  @Test
  void execute_missingPath_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("path", null);
    params.put("content", "x");
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_missingContent_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("path", "f.txt");
    params.put("content", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_traversalAttempt_returnsError() {
    assertThat(tool.execute(Map.of("path", "../evil.txt", "content", "x"))).startsWith("Error:");
  }

  @Test
  void execute_utf8ContentRoundTrip() throws Exception {
    var text = "Héllo wörld — 日本語";
    tool.execute(Map.of("path", "unicode.txt", "content", text));
    assertThat(Files.readString(tempDir.resolve("unicode.txt"), StandardCharsets.UTF_8))
        .isEqualTo(text);
  }
}
