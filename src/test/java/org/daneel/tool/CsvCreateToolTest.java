package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.daneel.tool.csv.CsvCreateTool;
import org.daneel.tool.csv.CsvSupport;
import org.daneel.tool.file.SandboxFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvCreateToolTest {

  @TempDir Path tempDir;

  private CsvCreateTool tool;
  private CsvSupport csvSupport;

  @BeforeEach
  void setUp() {
    csvSupport = new CsvSupport();
    tool = new CsvCreateTool(new SandboxFileSystem(tempDir.toString()), csvSupport);
  }

  @Test
  void execute_createsFileWithHeadersAndRows() throws Exception {
    var result =
        tool.execute(
            Map.of(
                "path",
                "out.csv",
                "headers",
                List.of("name", "email"),
                "rows",
                List.of("Ana,a@x.com", "Bob,b@x.com")));
    assertThat(result).doesNotStartWith("Error:");
    var rows = csvSupport.readRows(tempDir.resolve("out.csv"), null, 0);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("name", "Ana").containsEntry("email", "a@x.com");
  }

  @Test
  void execute_createsFileWithHeadersOnly_noRows() throws Exception {
    var result = tool.execute(Map.of("path", "headers-only.csv", "headers", List.of("id", "name")));
    assertThat(result).doesNotStartWith("Error:");
    assertThat(csvSupport.readRows(tempDir.resolve("headers-only.csv"), null, 0)).isEmpty();
  }

  @Test
  void execute_targetAlreadyExists_returnsError() throws Exception {
    Files.createFile(tempDir.resolve("existing.csv"));
    assertThat(tool.execute(Map.of("path", "existing.csv", "headers", List.of("id"))))
        .startsWith("Error:");
  }

  @Test
  void execute_rowCountMismatch_returnsError() {
    assertThat(
            tool.execute(
                Map.of(
                    "path", "bad.csv",
                    "headers", List.of("id", "name"),
                    "rows", List.of("only-one-field"))))
        .startsWith("Error:");
  }

  @Test
  void execute_missingHeaders_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("path", "out.csv");
    params.put("headers", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_traversalAttempt_returnsError() {
    assertThat(tool.execute(Map.of("path", "../evil.csv", "headers", List.of("id"))))
        .startsWith("Error:");
  }
}
