package org.daneel.tool.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
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

class CsvReadToolTest {

  @TempDir Path tempDir;

  private CsvReadTool tool;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    tool =
        new CsvReadTool(new SandboxFileSystem(tempDir.toString()), new CsvSupport(), objectMapper);
  }

  @Test
  void execute_returnsAllRows() throws Exception {
    Files.writeString(
        tempDir.resolve("data.csv"), "id,name\n1,Ana\n2,Bob\n", StandardCharsets.UTF_8);
    var result = tool.execute(Map.of("path", "data.csv"));
    var parsed =
        objectMapper.readValue(result, new TypeReference<Map<String, Map<String, String>>>() {});
    assertThat(parsed).hasSize(2);
    assertThat(parsed.get("0")).containsEntry("id", "1").containsEntry("name", "Ana");
    assertThat(parsed.get("1")).containsEntry("id", "2").containsEntry("name", "Bob");
  }

  @Test
  void execute_selectedColumns_returnsOnlyThoseColumns() throws Exception {
    Files.writeString(
        tempDir.resolve("data.csv"), "id,name,email\n1,Ana,a@x.com\n", StandardCharsets.UTF_8);
    var result = tool.execute(Map.of("path", "data.csv", "columns", List.of("name", "email")));
    var parsed =
        objectMapper.readValue(result, new TypeReference<Map<String, Map<String, String>>>() {});
    assertThat(parsed.get("0")).containsOnlyKeys("name", "email");
  }

  @Test
  void execute_withLimit_returnsAtMostLimitRows() throws Exception {
    Files.writeString(
        tempDir.resolve("data.csv"), "id,name\n1,Ana\n2,Bob\n3,Cam\n", StandardCharsets.UTF_8);
    var result = tool.execute(Map.of("path", "data.csv", "limit", "2"));
    var parsed =
        objectMapper.readValue(result, new TypeReference<Map<String, Map<String, String>>>() {});
    assertThat(parsed).hasSize(2);
  }

  @Test
  void execute_unknownColumn_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("data.csv"), "id,name\n1,Ana\n", StandardCharsets.UTF_8);
    assertThat(tool.execute(Map.of("path", "data.csv", "columns", List.of("nonexistent"))))
        .startsWith("Error:");
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
