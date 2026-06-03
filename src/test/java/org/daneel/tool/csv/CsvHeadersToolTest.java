package org.daneel.tool.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.daneel.tool.file.SandboxFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvHeadersToolTest {

  @TempDir Path tempDir;

  private CsvHeadersTool tool;

  @BeforeEach
  void setUp() {
    tool =
        new CsvHeadersTool(
            new SandboxFileSystem(tempDir.toString()), new CsvSupport(), new ObjectMapper());
  }

  @Test
  void execute_returnsHeadersAsJsonArray() throws Exception {
    Files.writeString(
        tempDir.resolve("data.csv"), "id,name,email\n1,Ana,a@x.com\n", StandardCharsets.UTF_8);
    var result = tool.execute(Map.of("path", "data.csv"));
    var headers = new ObjectMapper().readValue(result, List.class);
    assertThat(headers).containsExactly("id", "name", "email");
  }

  @Test
  void execute_missingPath_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("path", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_fileNotFound_returnsError() {
    assertThat(tool.execute(Map.of("path", "no-such.csv"))).startsWith("Error:");
  }

  @Test
  void execute_traversalAttempt_returnsError() {
    assertThat(tool.execute(Map.of("path", "../etc/passwd"))).startsWith("Error:");
  }
}
