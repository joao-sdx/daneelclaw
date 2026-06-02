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
public class CsvReadTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;
  private final CsvSupport csvSupport;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "csv_read";
  }

  @Override
  public String description() {
    return "Reads data rows from a CSV file. Returns a JSON object keyed by 0-based row index, "
        + "each value being a {column: value} map (header row excluded). "
        + "Optionally filter to specific columns and/or limit the number of rows. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "path", "Relative path to the CSV file inside the sandbox", "string", true),
        new ToolProperty(
            "columns",
            "Subset of column names to include in order; omit to return all columns",
            "array",
            false),
        new ToolProperty(
            "limit",
            "Maximum number of data rows to return; omit or 0 for all rows",
            "integer",
            false));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var pathRaw = params.get("path");
    if (pathRaw == null || pathRaw.toString().isBlank()) {
      return "Error: path parameter is required.";
    }
    List<String> columns = null;
    var colsRaw = params.get("columns");
    if (colsRaw instanceof List<?> list && !list.isEmpty()) {
      columns = list.stream().map(Object::toString).toList();
    }
    int limit = 0;
    var limitRaw = params.get("limit");
    if (limitRaw != null) {
      try {
        limit = Integer.parseInt(limitRaw.toString());
      } catch (NumberFormatException e) {
        return "Error: limit must be an integer.";
      }
    }
    try {
      var resolved = sandbox.resolve(pathRaw.toString());
      var rows = csvSupport.readRows(resolved, columns, limit);
      return objectMapper.writeValueAsString(rows);
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: file not found: " + pathRaw;
    } catch (IllegalArgumentException e) {
      return "Error: " + e.getMessage();
    } catch (IOException e) {
      return "Error: " + e.getMessage();
    }
  }
}
