package org.daneel.task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PromptCatalog {

  private static final Pattern ADHOC_PATTERN = Pattern.compile("^p\\d+\\.md$");

  private final String tasksDir;

  public PromptCatalog(@Value("${daneel.scheduler.tasks-dir:./tasks}") String tasksDir) {
    this.tasksDir = tasksDir;
  }

  @SneakyThrows
  public List<PromptSummary> list() {
    var dir = Path.of(tasksDir);
    if (!Files.exists(dir)) {
      return List.of();
    }
    try (var stream = Files.list(dir)) {
      return stream
          .filter(p -> p.getFileName().toString().endsWith(".md"))
          .map(this::toSummary)
          .toList();
    }
  }

  public boolean exists(String promptFile) {
    return Files.exists(Path.of(tasksDir, promptFile));
  }

  public boolean isAdhoc(String promptFile) {
    return promptFile != null && ADHOC_PATTERN.matcher(promptFile).matches();
  }

  @SneakyThrows
  public void delete(String promptFile) {
    Files.deleteIfExists(Path.of(tasksDir, promptFile));
  }

  @SneakyThrows
  private PromptSummary toSummary(Path path) {
    var content = Files.readString(path);
    var doc = PromptDocument.parse(content);
    return new PromptSummary(path.getFileName().toString(), doc.summary());
  }
}
