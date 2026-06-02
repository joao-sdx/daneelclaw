package org.daneel.tool.csv;

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
public class CsvAppendTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;
  private final CsvSupport csvSupport;

  @Override
  public String name() {
    return "csv_append";
  }

  @Override
  public String description() {
    return "Appends one data row to an existing CSV file. "
        + "The number of values must exactly match the file's column count. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "path", "Relative path to the CSV file inside the sandbox", "string", true),
        new ToolProperty(
            "values",
            "List of field values to append; must match the CSV column count in order",
            "array",
            true));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var pathRaw = params.get("path");
    if (pathRaw == null || pathRaw.toString().isBlank()) {
      return "Error: path parameter is required.";
    }
    var valuesRaw = params.get("values");
    if (!(valuesRaw instanceof List<?> valueList) || valueList.isEmpty()) {
      return "Error: values must be a non-empty list.";
    }
    var values = valueList.stream().map(Object::toString).toList();
    try {
      var resolved = sandbox.resolve(pathRaw.toString());
      var headers = csvSupport.readHeaders(resolved);
      if (values.size() != headers.size()) {
        return "Error: "
            + values.size()
            + " values provided but CSV has "
            + headers.size()
            + " columns.";
      }
      csvSupport.appendRecord(resolved, values);
      return "Appended 1 row to " + pathRaw + ".";
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: file not found: " + pathRaw;
    } catch (IOException e) {
      return "Error: " + e.getMessage();
    }
  }
}
