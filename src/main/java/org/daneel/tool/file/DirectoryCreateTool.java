package org.daneel.tool.file;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectoryCreateTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;

  @Override
  public String name() {
    return "directory_create";
  }

  @Override
  public String description() {
    return "Creates a directory (and any missing parent directories) inside the sandbox. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("path", "Relative path of the directory to create", "string", true));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var pathRaw = params.get("path");
    if (pathRaw == null || pathRaw.toString().isBlank()) {
      return "Error: path parameter is required.";
    }
    try {
      var resolved = sandbox.resolve(pathRaw.toString());
      Files.createDirectories(resolved);
      log.info("directory_create path={}", pathRaw);
      return "Created directory " + pathRaw + ".";
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    }
  }
}
