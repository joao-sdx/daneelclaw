package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.daneel.task.PromptDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptCreateToolTest {

  @TempDir Path tempDir;

  private PromptCreateTool tool;

  @BeforeEach
  void setUp() {
    tool = new PromptCreateTool(tempDir.toString(), new ObjectMapper());
  }

  @Test
  void execute_createsFileAndReturnsJson() throws Exception {
    var result =
        tool.execute(
            Map.of(
                "summary", "Says hello.",
                "body", "Use the speak tool to say hello."));
    // result is JSON with promptFile and summary
    assertThat(result).contains("Says hello.");
    // exactly one .md file was created
    var files =
        Files.list(tempDir).filter(p -> p.getFileName().toString().endsWith(".md")).toList();
    assertThat(files).hasSize(1);
    // filename starts with 'p' and ends with '.md'
    assertThat(files.getFirst().getFileName().toString()).startsWith("p").endsWith(".md");
    // file content parses back correctly
    var content = Files.readString(files.getFirst());
    var doc = PromptDocument.parse(content);
    assertThat(doc.summary()).isEqualTo("Says hello.");
    assertThat(doc.body().strip()).isEqualTo("Use the speak tool to say hello.");
  }

  @Test
  void execute_missingSummary_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("summary", null);
    params.put("body", "Some body.");
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_missingBody_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("summary", "A summary.");
    params.put("body", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_twoCallsProduceTwoDistinctFiles() throws Exception {
    tool.execute(Map.of("summary", "First.", "body", "Body one."));
    tool.execute(Map.of("summary", "Second.", "body", "Body two."));
    var files =
        Files.list(tempDir).filter(p -> p.getFileName().toString().endsWith(".md")).toList();
    assertThat(files).hasSize(2);
  }
}
