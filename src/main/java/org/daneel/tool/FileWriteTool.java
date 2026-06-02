package org.daneel.tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileWriteTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;

  @Override
  public String name() {
    return "file_write";
  }

  @Override
  public String description() {
    return "Writes text content to a file inside the sandbox, overwriting it if it already exists. "
        + "Parent directories are created automatically. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("path", "Relative path to the file inside the sandbox", "string", true),
        new ToolProperty("content", "UTF-8 text content to write", "string", true));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var pathRaw = params.get("path");
    if (pathRaw == null || pathRaw.toString().isBlank()) {
      return "Error: path parameter is required.";
    }
    var contentRaw = params.get("content");
    if (contentRaw == null) {
      return "Error: content parameter is required.";
    }
    try {
      var resolved = sandbox.resolve(pathRaw.toString());
      var content = contentRaw.toString();
      Files.createDirectories(resolved.getParent());
      Files.writeString(resolved, content, StandardCharsets.UTF_8);
      log.info(
          "file_write path={} bytes={}", pathRaw, content.getBytes(StandardCharsets.UTF_8).length);
      return "Wrote "
          + content.getBytes(StandardCharsets.UTF_8).length
          + " bytes to "
          + pathRaw
          + ".";
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    }
  }
}
