package org.daneel.tool.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeoSearchTool implements DaneelToolInterface {

  private final DataForSeoClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "seo_search";
  }

  @Override
  public String description() {
    return "Searches Google News via DataForSEO and returns a JSON array of articles. "
        + "Each article has result_id, title, url, domain, and published fields. "
        + "Use result_id with seo_fetch_article to download article content.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("keyword", "Search term", "string", true),
        new ToolProperty("language", "Language code (default: en)", "string", false),
        new ToolProperty("location", "Location name (default: United States)", "string", false),
        new ToolProperty(
            "depth", "Max number of results to return (default: 10)", "integer", false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var keyword = params.get("keyword");
    if (keyword == null || keyword.toString().isBlank()) {
      return "Error: keyword is required";
    }
    var language = params.getOrDefault("language", "en").toString();
    var location = params.getOrDefault("location", "United States").toString();
    int depth = parseDepth(params.get("depth"));
    try {
      var articles = client.searchNews(keyword.toString(), language, location, depth);
      return objectMapper.writeValueAsString(articles);
    } catch (Exception e) {
      log.warn("seo_search_failed keyword={} reason={}", keyword, e.getMessage());
      return "Error: DataForSEO search failed: " + e.getMessage();
    }
  }

  private int parseDepth(Object raw) {
    if (raw == null) {
      return 10;
    }
    try {
      return Integer.parseInt(raw.toString());
    } catch (NumberFormatException e) {
      return 10;
    }
  }
}
