package org.daneel.tool.seo;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.file.SandboxAccessException;
import org.daneel.tool.file.SandboxFileSystem;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeoSearchAndFetchTool implements DaneelToolInterface {

  private final DataForSeoClient client;
  private final SandboxFileSystem sandbox;

  @Override
  public String name() {
    return "seo_search_and_fetch";
  }

  @Override
  public String description() {
    return "Searches Google News via DataForSEO and saves every result's content as a Markdown file "
        + "in a sandbox directory. Combines seo_search + seo_fetch_article in one call — "
        + "use this when you want to retrieve and persist all articles for a keyword.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("keyword", "Search term", "string", true),
        new ToolProperty(
            "directory",
            "Sandbox-relative directory to save articles (e.g. seo/cto)",
            "string",
            true),
        new ToolProperty("language", "Language code (default: en)", "string", false),
        new ToolProperty("location", "Location name (default: United States)", "string", false),
        new ToolProperty("depth", "Max number of results (default: 10)", "integer", false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var keyword = params.get("keyword");
    if (keyword == null || keyword.toString().isBlank()) {
      return "Error: keyword is required";
    }
    var directory = params.get("directory");
    if (directory == null || directory.toString().isBlank()) {
      return "Error: directory is required";
    }
    var language = params.getOrDefault("language", "en").toString();
    var location = params.getOrDefault("location", "United States").toString();
    int depth = parseDepth(params.get("depth"));

    try {
      var targetDir = sandbox.resolve(directory.toString());
      Files.createDirectories(targetDir);

      var articles = client.searchNews(keyword.toString(), language, location, depth);
      int saved = 0;
      var failed = new ArrayList<String>();

      for (var article : articles) {
        try {
          var content = client.fetchContent(article.url());
          var fileContent = buildMarkdown(article, keyword.toString(), content);
          var filePath = targetDir.resolve(article.resultId() + ".md");
          Files.writeString(filePath, fileContent);
          log.info("seo_article_saved file={}/{}.md", directory, article.resultId());
          saved++;
        } catch (Exception e) {
          log.warn("seo_fetch_failed result_id={} reason={}", article.resultId(), e.getMessage());
          failed.add(article.resultId());
        }
      }

      var msg = "Saved " + saved + "/" + articles.size() + " articles to " + directory;
      if (!failed.isEmpty()) {
        msg += ". Failed: " + failed;
      }
      return msg;
    } catch (SandboxAccessException e) {
      return "Error: access denied";
    } catch (Exception e) {
      log.warn("seo_search_and_fetch_failed keyword={} reason={}", keyword, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }

  private String buildMarkdown(NewsArticle article, String keyword, String content) {
    var sb = new StringBuilder();
    sb.append("---\n");
    sb.append("result_id: ").append(article.resultId()).append("\n");
    sb.append("url: ").append(article.url()).append("\n");
    sb.append("keyword: ").append(keyword).append("\n");
    if (article.title() != null) {
      sb.append("title: \"").append(article.title().replace("\"", "'")).append("\"\n");
    }
    if (article.published() != null) {
      sb.append("published: ").append(article.published()).append("\n");
    }
    sb.append("---\n");
    if (content != null && !content.isBlank()) {
      sb.append("\n").append(content);
    }
    return sb.toString();
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
