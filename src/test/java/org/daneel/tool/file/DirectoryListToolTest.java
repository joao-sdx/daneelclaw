package org.daneel.tool.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryListToolTest {

  @TempDir Path tempDir;

  private DirectoryListTool tool;

  @BeforeEach
  void setUp() {
    tool = new DirectoryListTool(new SandboxFileSystem(tempDir.toString()), new ObjectMapper());
  }

  @Test
  void execute_listsFilesAndDirs() throws Exception {
    Files.writeString(tempDir.resolve("a.txt"), "hello");
    Files.createDirectories(tempDir.resolve("subdir"));
    var result = tool.execute(Map.of("path", ""));
    assertThat(result).contains("\"name\":\"a.txt\"");
    assertThat(result).contains("\"type\":\"file\"");
    assertThat(result).contains("\"name\":\"subdir\"");
    assertThat(result).contains("\"type\":\"directory\"");
  }

  @Test
  void execute_omittedPath_listsRoot() throws Exception {
    Files.writeString(tempDir.resolve("root-file.txt"), "x");
    var params = new HashMap<String, Object>();
    // no "path" key
    var result = tool.execute(params);
    assertThat(result).contains("root-file.txt");
  }

  @Test
  void execute_subDirectory_listsContents() throws Exception {
    Files.createDirectories(tempDir.resolve("sub"));
    Files.writeString(tempDir.resolve("sub/note.md"), "note");
    var result = tool.execute(Map.of("path", "sub"));
    assertThat(result).contains("note.md");
  }

  @Test
  void execute_sizeBytes_reportedForFiles() throws Exception {
    Files.writeString(tempDir.resolve("sized.txt"), "12345");
    var result = tool.execute(Map.of("path", ""));
    assertThat(result).contains("\"sizeBytes\":5");
  }

  @Test
  void execute_emptyDirectory_returnsEmptyArray() throws Exception {
    Files.createDirectories(tempDir.resolve("empty"));
    var result = tool.execute(Map.of("path", "empty"));
    assertThat(result).isEqualTo("[]");
  }

  @Test
  void execute_pathNotFound_returnsError() {
    assertThat(tool.execute(Map.of("path", "ghost"))).startsWith("Error:");
  }

  @Test
  void execute_pathIsFile_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("file.txt"), "x");
    assertThat(tool.execute(Map.of("path", "file.txt"))).startsWith("Error:");
  }

  @Test
  void execute_traversalAttempt_returnsError() {
    assertThat(tool.execute(Map.of("path", "../"))).startsWith("Error:");
  }
}
