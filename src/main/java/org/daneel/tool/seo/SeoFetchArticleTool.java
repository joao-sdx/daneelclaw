package org.daneel.tool.seo;

import java.nio.file.Files;
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
public class SeoFetchArticleTool implements DaneelToolInterface {

  private final DataForSeoClient client;
  private final SandboxFileSystem sandbox;

  @Override
  public String name() {
    return "seo_fetch_article";
  }

  @Override
  public String description() {
    return "Fetches the content of a news article via DataForSEO and saves it as a Markdown file "
        + "in the sandbox. The file is saved as {result_id}.md with YAML frontmatter. "
        + "Use seo_search first to get the result_id and url.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("result_id", "Article ID from seo_search results", "string", true),
        new ToolProperty("url", "Article URL to fetch content from", "string", true),
        new ToolProperty("keyword", "Optional: stored in YAML frontmatter", "string", false),
        new ToolProperty(
            "return_text",
            "If true, returns the full file content in the response (default: false)",
            "boolean",
            false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var resultId = params.get("result_id");
    if (resultId == null || resultId.toString().isBlank()) {
      return "Error: result_id is required";
    }
    var url = params.get("url");
    if (url == null || url.toString().isBlank()) {
      return "Error: url is required";
    }
    var keyword = params.get("keyword");
    var returnText = Boolean.parseBoolean(params.getOrDefault("return_text", "false").toString());
    try {
      var content = client.fetchContent(url.toString());
      var fileContent = buildMarkdown(resultId.toString(), url.toString(), keyword, content);
      var filePath = sandbox.resolve(resultId + ".md");
      Files.writeString(filePath, fileContent);
      log.info("seo_article_saved file={}.md", resultId);
      return returnText ? fileContent : "Saved to " + resultId + ".md";
    } catch (SandboxAccessException e) {
      return "Error: access denied";
    } catch (Exception e) {
      log.warn("seo_fetch_failed result_id={} reason={}", resultId, e.getMessage());
      return "Error: failed to fetch article: " + e.getMessage();
    }
  }

  private String buildMarkdown(String resultId, String url, Object keyword, String content) {
    var sb = new StringBuilder();
    sb.append("---\n");
    sb.append("result_id: ").append(resultId).append("\n");
    sb.append("url: ").append(url).append("\n");
    if (keyword != null && !keyword.toString().isBlank()) {
      sb.append("keyword: ").append(keyword).append("\n");
    }
    sb.append("---\n");
    if (content != null && !content.isBlank()) {
      sb.append("\n").append(content);
    }
    return sb.toString();
  }
}
