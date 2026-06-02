package org.daneel.tool.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeoSearchToolTest {

  @Mock private DataForSeoClient client;

  private SeoSearchTool tool;

  @BeforeEach
  void setUp() {
    tool = new SeoSearchTool(client, new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsJsonArray() throws Exception {
    when(client.searchNews("ai", "en", "United States", 10))
        .thenReturn(
            List.of(
                new NewsArticle("id1", "Title 1", "https://a.com", "a.com", "2024-01-15"),
                new NewsArticle("id2", "Title 2", "https://b.com", "b.com", "2024-01-16")));

    var result = tool.execute(Map.of("keyword", "ai"));

    var articles = new ObjectMapper().readValue(result, List.class);
    assertThat(articles).hasSize(2);
  }

  @Test
  void execute_missingKeyword_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("keyword", null);
    assertThat(tool.execute(params)).isEqualTo("Error: keyword is required");
  }

  @Test
  void execute_blankKeyword_returnsError() {
    assertThat(tool.execute(Map.of("keyword", "  "))).isEqualTo("Error: keyword is required");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.searchNews(any(), any(), any(), anyInt()))
        .thenThrow(new RuntimeException("connection timeout"));

    var result = tool.execute(Map.of("keyword", "test"));

    assertThat(result).startsWith("Error: DataForSEO search failed:");
    assertThat(result).contains("connection timeout");
  }
}
