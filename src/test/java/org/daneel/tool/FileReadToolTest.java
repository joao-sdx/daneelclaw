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

class FileReadToolTest {

  @TempDir Path tempDir;

  private FileReadTool tool;

  @BeforeEach
  void setUp() {
    tool = new FileReadTool(new SandboxFileSystem(tempDir.toString()));
  }

  @Test
  void execute_readsExistingFile() throws Exception {
    Files.writeString(tempDir.resolve("hello.txt"), "Hello, world!", StandardCharsets.UTF_8);
    assertThat(tool.execute(Map.of("path", "hello.txt"))).isEqualTo("Hello, world!");
  }

  @Test
  void execute_readsFileInSubdirectory() throws Exception {
    Files.createDirectories(tempDir.resolve("sub"));
    Files.writeString(tempDir.resolve("sub/note.txt"), "sub-content", StandardCharsets.UTF_8);
    assertThat(tool.execute(Map.of("path", "sub/note.txt"))).isEqualTo("sub-content");
  }

  @Test
  void execute_missingPath_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("path", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_fileNotFound_returnsError() {
    assertThat(tool.execute(Map.of("path", "no-such-file.txt"))).startsWith("Error:");
  }

  @Test
  void execute_traversalAttempt_returnsError() {
    assertThat(tool.execute(Map.of("path", "../etc/passwd"))).startsWith("Error:");
  }

  @Test
  void execute_binaryFile_returnsError() throws Exception {
    // Write bytes that are not valid UTF-8 (0xFF, 0xFE are invalid UTF-8 start bytes)
    var path = tempDir.resolve("binary.bin");
    Files.write(path, new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01});
    assertThat(tool.execute(Map.of("path", "binary.bin"))).startsWith("Error:");
  }
}
