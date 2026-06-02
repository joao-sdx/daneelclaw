package org.daneel.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.util.Map;
import lombok.SneakyThrows;

public record PromptDocument(String summary, String body) {

  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

  public static PromptDocument parse(String content) {
    if (content == null || !content.startsWith("---\n")) {
      return new PromptDocument("", content != null ? content : "");
    }
    var closing = content.indexOf("\n---", 4);
    if (closing == -1) {
      return new PromptDocument("", content);
    }
    var yamlBlock = content.substring(4, closing);
    var afterClosing = content.substring(closing + 4);
    var body = afterClosing.stripLeading();
    return new PromptDocument(extractSummary(yamlBlock), body);
  }

  @SneakyThrows
  public static String render(String summary, String body) {
    var frontmatter = YAML_MAPPER.writeValueAsString(Map.of("summary", summary));
    return "---\n" + frontmatter + "---\n" + body + "\n";
  }

  @SneakyThrows
  private static String extractSummary(String yaml) {
    var node = YAML_MAPPER.readTree(yaml);
    if (node == null) {
      return "";
    }
    var summaryNode = node.get("summary");
    return summaryNode != null ? summaryNode.asText("") : "";
  }
}
