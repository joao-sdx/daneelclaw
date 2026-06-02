package org.daneel.tool.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
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
public class FileDeleteTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;

  @Override
  public String name() {
    return "file_delete";
  }

  @Override
  public String description() {
    return "Deletes a file or directory (recursively) inside the sandbox. "
        + "Cannot delete the sandbox root itself. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "path", "Relative path to the file or directory to delete", "string", true));
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
      if (resolved.equals(sandbox.root())) {
        return "Error: cannot delete the sandbox root directory.";
      }
      if (!Files.exists(resolved)) {
        return "Error: path not found: " + pathRaw;
      }
      if (Files.isDirectory(resolved)) {
        deleteRecursive(resolved);
        log.info("file_delete_dir path={}", pathRaw);
        return "Deleted directory " + pathRaw + ".";
      } else {
        Files.delete(resolved);
        log.info("file_delete path={}", pathRaw);
        return "Deleted " + pathRaw + ".";
      }
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: path not found: " + pathRaw;
    }
  }

  @SneakyThrows
  private void deleteRecursive(Path dir) {
    try (var stream = Files.walk(dir)) {
      var paths = stream.sorted(Comparator.reverseOrder()).toList();
      for (var p : paths) {
        try {
          Files.delete(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
