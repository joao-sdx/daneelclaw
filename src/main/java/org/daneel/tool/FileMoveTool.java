package org.daneel.tool;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileMoveTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;

  @Override
  public String name() {
    return "file_move";
  }

  @Override
  public String description() {
    return "Moves or renames a file or directory within the sandbox. "
        + "The destination's parent directories are created automatically. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("source", "Relative path of the source file or directory", "string", true),
        new ToolProperty("destination", "Relative path of the destination", "string", true));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var sourceRaw = params.get("source");
    if (sourceRaw == null || sourceRaw.toString().isBlank()) {
      return "Error: source parameter is required.";
    }
    var destRaw = params.get("destination");
    if (destRaw == null || destRaw.toString().isBlank()) {
      return "Error: destination parameter is required.";
    }
    try {
      var source = sandbox.resolve(sourceRaw.toString());
      var dest = sandbox.resolve(destRaw.toString());
      if (!Files.exists(source)) {
        return "Error: source not found: " + sourceRaw;
      }
      Files.createDirectories(dest.getParent());
      Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
      log.info("file_move source={} destination={}", sourceRaw, destRaw);
      return "Moved " + sourceRaw + " to " + destRaw + ".";
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: source not found: " + sourceRaw;
    }
  }
}
