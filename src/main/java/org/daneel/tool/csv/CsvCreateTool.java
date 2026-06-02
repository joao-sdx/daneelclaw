package org.daneel.tool.csv;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
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
public class CsvCreateTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;
  private final CsvSupport csvSupport;

  @Override
  public String name() {
    return "csv_create";
  }

  @Override
  public String description() {
    return "Creates a new CSV file with the given column headers and optional data rows. "
        + "Fails if the file already exists — delete it first if you want to replace it. "
        + "Each row in 'rows' must be a CSV-encoded string with exactly as many fields as headers. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("path", "Relative path for the new CSV file", "string", true),
        new ToolProperty("headers", "List of column header names", "array", true),
        new ToolProperty(
            "rows",
            "Optional list of CSV-encoded data rows; each element is one record matching the column order",
            "array",
            false));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var pathRaw = params.get("path");
    if (pathRaw == null || pathRaw.toString().isBlank()) {
      return "Error: path parameter is required.";
    }
    var headersRaw = params.get("headers");
    if (!(headersRaw instanceof List<?> headerList) || headerList.isEmpty()) {
      return "Error: headers must be a non-empty list.";
    }
    var headers = headerList.stream().map(Object::toString).toList();
    var rows = new ArrayList<List<String>>();
    var rowsRaw = params.get("rows");
    if (rowsRaw instanceof List<?> rowList) {
      for (var rowObj : rowList) {
        try {
          var parsed = csvSupport.parseRecord(rowObj.toString());
          if (parsed.size() != headers.size()) {
            return "Error: row has "
                + parsed.size()
                + " fields but expected "
                + headers.size()
                + ": "
                + rowObj;
          }
          rows.add(parsed);
        } catch (IOException e) {
          return "Error: could not parse row: " + rowObj;
        }
      }
    }
    try {
      var resolved = sandbox.resolve(pathRaw.toString());
      if (Files.exists(resolved)) {
        return "Error: file already exists: " + pathRaw;
      }
      Files.createDirectories(resolved.getParent());
      csvSupport.write(resolved, headers, rows);
      return "Created " + pathRaw + " with " + rows.size() + " data rows.";
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (IOException e) {
      return "Error: " + e.getMessage();
    }
  }
}
