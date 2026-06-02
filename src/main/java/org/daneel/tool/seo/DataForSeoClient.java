package org.daneel.tool.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataForSeoClient {

  private static final String NEWS_ENDPOINT =
      "https://api.dataforseo.com/v3/serp/google/news/live/advanced";
  private static final String CONTENT_ENDPOINT =
      "https://api.dataforseo.com/v3/on_page/content_parsing/live";

  private final DataForSeoProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  public List<NewsArticle> searchNews(String keyword, String language, String location, int depth)
      throws Exception {
    var body =
        objectMapper.writeValueAsString(
            List.of(
                Map.of(
                    "keyword", keyword,
                    "language_code", language,
                    "location_name", location,
                    "depth", depth)));
    var raw = post(NEWS_ENDPOINT, body);
    log.debug("raw: {}", raw);
    var items =
        objectMapper.readTree(raw).path("tasks").path(0).path("result").path(0).path("items");
    var result = new ArrayList<NewsArticle>();
    for (var item : items) {
      var url = item.path("url").asText(null);
      if (url == null) {
        continue;
      }
      // DataForSEO news items have no per-item ID; derive a stable one from the URL hash
      var resultId = String.format("%08x", url.hashCode() & 0xFFFFFFFFL);
      result.add(
          new NewsArticle(
              resultId,
              item.path("title").asText(null),
              url,
              item.path("domain").asText(null),
              item.path("time_published").asText(null)));
    }
    log.info("seo_news_found keyword={} count={}", keyword, result.size());
    return result;
  }

  public String fetchContent(String url) throws Exception {
    var body =
        objectMapper.writeValueAsString(List.of(objectMapper.createObjectNode().put("url", url)));
    var raw = post(CONTENT_ENDPOINT, body);
    log.debug("seo_content_found {}", raw);
    var topics =
        objectMapper
            .readTree(raw)
            .path("tasks")
            .path(0)
            .path("result")
            .path(0)
            .path("items")
            .path(0)
            .path("page_content")
            .path("main_topic");
    var md = new StringBuilder();
    for (var topic : topics) {
      var title = topic.path("h_title").asText("").strip();
      if (!title.isBlank()) {
        md.append("## ").append(title).append("\n\n");
      }
      for (var content : topic.path("primary_content")) {
        var text = content.path("text").asText("").strip();
        if (!text.isBlank()) {
          md.append(text).append("\n\n");
        }
      }
    }
    var result = md.toString().strip();
    log.info("seo_content_fetched url={} chars={}", url, result.length());
    return result;
  }

  private String post(String endpoint, String body) throws Exception {
    var credentials = properties.getUser() + ":" + properties.getKey();
    var auth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Authorization", "Basic " + auth)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    log.debug("dataforseo_post endpoint={} status={}", endpoint, response.statusCode());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("DataForSEO error status=" + response.statusCode());
    }
    return response.body();
  }
}
