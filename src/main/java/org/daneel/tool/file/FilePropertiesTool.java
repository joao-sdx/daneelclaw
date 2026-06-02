package org.daneel.tool.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FilePropertiesTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;
  private final ObjectMapper objectMapper;

  private record FileInfo(
      String name,
      String relativePath,
      String type,
      long sizeBytes,
      String lastModified,
      boolean readable,
      boolean writable) {}

  @Override
  public String name() {
    return "file_properties";
  }

  @Override
  public String description() {
    return "Returns metadata about a file or directory inside the sandbox as JSON: "
        + "name, relativePath, type (file/directory), sizeBytes, lastModified (ISO), readable, writable. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "path", "Relative path to the file or directory inside the sandbox", "string", true));
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
      if (!Files.exists(resolved)) {
        return "Error: path not found: " + pathRaw;
      }
      var type = Files.isDirectory(resolved) ? "directory" : "file";
      var sizeBytes = Files.isDirectory(resolved) ? 0L : Files.size(resolved);
      var lastModified = Files.getLastModifiedTime(resolved).toInstant().toString();
      var relativePath = sandbox.root().relativize(resolved).toString();
      var info =
          new FileInfo(
              resolved.getFileName().toString(),
              relativePath,
              type,
              sizeBytes,
              lastModified,
              Files.isReadable(resolved),
              Files.isWritable(resolved));
      return objectMapper.writeValueAsString(info);
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: path not found: " + pathRaw;
    }
  }
}
