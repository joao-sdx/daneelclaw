package org.daneel.tool;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileReadTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;

  @Override
  public String name() {
    return "file_read";
  }

  @Override
  public String description() {
    return "Reads the contents of a UTF-8 text file inside the sandbox. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("path", "Relative path to the file inside the sandbox", "string", true));
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
      return Files.readString(resolved, StandardCharsets.UTF_8);
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (MalformedInputException e) {
      return "Error: not a UTF-8 text file.";
    } catch (NoSuchFileException e) {
      return "Error: file not found: " + pathRaw;
    }
  }
}
