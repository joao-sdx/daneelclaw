package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectoryListTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;
  private final ObjectMapper objectMapper;

  private record FileEntry(String name, String type, long sizeBytes) {}

  @Override
  public String name() {
    return "directory_list";
  }

  @Override
  public String description() {
    return "Lists the immediate contents of a directory inside the sandbox as a JSON array. "
        + "Each entry has name, type (file/directory), and sizeBytes. "
        + "If path is omitted, lists the sandbox root. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "path",
            "Relative path of the directory to list; omit or leave blank for the sandbox root",
            "string",
            false));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var pathRaw = params.get("path");
    var pathStr = (pathRaw == null) ? "" : pathRaw.toString();
    try {
      var resolved = sandbox.resolve(pathStr);
      if (!Files.exists(resolved)) {
        return "Error: directory not found: " + pathStr;
      }
      if (!Files.isDirectory(resolved)) {
        return "Error: path is not a directory: " + pathStr;
      }
      List<FileEntry> entries;
      try (var stream = Files.list(resolved)) {
        entries =
            stream
                .map(
                    p -> {
                      var type = Files.isDirectory(p) ? "directory" : "file";
                      long size = 0;
                      if (!Files.isDirectory(p)) {
                        try {
                          size = Files.size(p);
                        } catch (Exception ignored) {
                        }
                      }
                      return new FileEntry(p.getFileName().toString(), type, size);
                    })
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
      }
      return objectMapper.writeValueAsString(entries);
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: directory not found: " + pathStr;
    }
  }
}
