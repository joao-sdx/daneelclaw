package org.daneel.tool.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.file.SandboxFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeoFetchArticleToolTest {

  @TempDir Path tempDir;

  @Mock private DataForSeoClient client;

  private SeoFetchArticleTool tool;

  @BeforeEach
  void setUp() {
    tool = new SeoFetchArticleTool(client, new SandboxFileSystem(tempDir.toString()));
  }

  @Test
  void execute_happyPath_savesFileAndReturnsPath() throws Exception {
    when(client.fetchContent("https://example.com/article")).thenReturn("Article content here.");

    var result = tool.execute(Map.of("result_id", "abc123", "url", "https://example.com/article"));

    assertThat(result).isEqualTo("Saved to abc123.md");
    var saved = Files.readString(tempDir.resolve("abc123.md"));
    assertThat(saved).contains("result_id: abc123");
    assertThat(saved).contains("url: https://example.com/article");
    assertThat(saved).contains("Article content here.");
  }

  @Test
  void execute_withKeyword_includesKeywordInFrontmatter() throws Exception {
    when(client.fetchContent(any())).thenReturn("Body text.");

    var result =
        tool.execute(
            Map.of("result_id", "abc123", "url", "https://example.com", "keyword", "test query"));

    var saved = Files.readString(tempDir.resolve("abc123.md"));
    assertThat(saved).contains("keyword: test query");
    assertThat(result).isEqualTo("Saved to abc123.md");
  }

  @Test
  void execute_returnText_returnsFullFileContent() throws Exception {
    when(client.fetchContent(any())).thenReturn("Article body.");

    var result =
        tool.execute(
            Map.of(
                "result_id", "abc123",
                "url", "https://example.com/article",
                "return_text", "true"));

    assertThat(result).contains("result_id: abc123");
    assertThat(result).contains("Article body.");
  }

  @Test
  void execute_missingResultId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("result_id", null);
    params.put("url", "https://example.com");
    assertThat(tool.execute(params)).isEqualTo("Error: result_id is required");
  }

  @Test
  void execute_missingUrl_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("result_id", "abc123");
    params.put("url", null);
    assertThat(tool.execute(params)).isEqualTo("Error: url is required");
  }

  @Test
  void execute_traversalAttempt_returnsAccessDenied() throws Exception {
    when(client.fetchContent(any())).thenReturn("content");

    var result = tool.execute(Map.of("result_id", "../etc/passwd", "url", "https://example.com"));

    assertThat(result).isEqualTo("Error: access denied");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.fetchContent(any())).thenThrow(new RuntimeException("connection refused"));

    var result = tool.execute(Map.of("result_id", "abc123", "url", "https://example.com/article"));

    assertThat(result).startsWith("Error: failed to fetch article:");
    assertThat(result).contains("connection refused");
  }
}
