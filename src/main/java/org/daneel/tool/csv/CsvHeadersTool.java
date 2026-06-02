package org.daneel.tool.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.file.SandboxAccessException;
import org.daneel.tool.file.SandboxFileSystem;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CsvHeadersTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;
  private final CsvSupport csvSupport;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "csv_headers";
  }

  @Override
  public String description() {
    return "Returns the column headers of a CSV file as a JSON array. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "path", "Relative path to the CSV file inside the sandbox", "string", true));
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
      var headers = csvSupport.readHeaders(resolved);
      return objectMapper.writeValueAsString(headers);
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: file not found: " + pathRaw;
    } catch (IOException e) {
      return "Error: " + e.getMessage();
    }
  }
}
