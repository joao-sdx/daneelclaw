package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePropertiesToolTest {

  @TempDir Path tempDir;

  private FilePropertiesTool tool;

  @BeforeEach
  void setUp() {
    tool = new FilePropertiesTool(new SandboxFileSystem(tempDir.toString()), new ObjectMapper());
  }

  @Test
  void execute_fileProperties_returnsJson() throws Exception {
    Files.writeString(tempDir.resolve("info.txt"), "hello");
    var result = tool.execute(Map.of("path", "info.txt"));
    assertThat(result).contains("\"name\":\"info.txt\"");
    assertThat(result).contains("\"type\":\"file\"");
    assertThat(result).contains("\"sizeBytes\":5");
    assertThat(result).contains("\"lastModified\"");
    assertThat(result).contains("\"readable\"");
    assertThat(result).contains("\"writable\"");
  }

  @Test
  void execute_directoryProperties_returnsTypeDirectory() throws Exception {
    Files.createDirectories(tempDir.resolve("mydir"));
    var result = tool.execute(Map.of("path", "mydir"));
    assertThat(result).contains("\"type\":\"directory\"");
    assertThat(result).contains("\"sizeBytes\":0");
  }

  @Test
  void execute_relativePathInJson() throws Exception {
    Files.createDirectories(tempDir.resolve("sub"));
    Files.writeString(tempDir.resolve("sub/f.txt"), "x");
    var result = tool.execute(Map.of("path", "sub/f.txt"));
    assertThat(result).contains("\"relativePath\":\"sub/f.txt\"");
  }

  @Test
  void execute_pathNotFound_returnsError() {
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
    assertThat(tool.execute(Map.of("path", "../etc/passwd"))).startsWith("Error:");
  }
}
