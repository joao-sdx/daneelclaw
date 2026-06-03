package org.daneel.tool.csv;

import static org.assertj.core.api.Assertions.assertThat;

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

class CsvAppendToolTest {

  @TempDir Path tempDir;

  private CsvAppendTool tool;
  private CsvSupport csvSupport;

  @BeforeEach
  void setUp() {
    csvSupport = new CsvSupport();
    tool = new CsvAppendTool(new SandboxFileSystem(tempDir.toString()), csvSupport);
  }

  @Test
  void execute_appendsRowToExistingCsv() throws Exception {
    Files.writeString(tempDir.resolve("data.csv"), "id,name\n1,Ana\n", StandardCharsets.UTF_8);
    var result = tool.execute(Map.of("path", "data.csv", "values", List.of("2", "Bob")));
    assertThat(result).doesNotStartWith("Error:");
    var rows = csvSupport.readRows(tempDir.resolve("data.csv"), null, 0);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(1)).containsEntry("id", "2").containsEntry("name", "Bob");
  }

  @Test
  void execute_columnCountMismatch_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("data.csv"), "id,name\n1,Ana\n", StandardCharsets.UTF_8);
    assertThat(tool.execute(Map.of("path", "data.csv", "values", List.of("2"))))
        .startsWith("Error:");
  }

  @Test
  void execute_fileNotFound_returnsError() {
    assertThat(tool.execute(Map.of("path", "no-such.csv", "values", List.of("1", "Ana"))))
        .startsWith("Error:");
  }

  @Test
  void execute_missingValues_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("path", "data.csv");
    params.put("values", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_traversalAttempt_returnsError() {
    assertThat(tool.execute(Map.of("path", "../evil.csv", "values", List.of("x"))))
        .startsWith("Error:");
  }
}
