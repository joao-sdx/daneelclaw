package org.daneel.tool.csv;

import java.io.IOException;
import java.nio.file.Files;
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
public class CsvFilterCopyTool implements DaneelToolInterface {

  private final SandboxFileSystem sandbox;
  private final CsvSupport csvSupport;

  @Override
  public String name() {
    return "csv_filter_copy";
  }

  @Override
  public String description() {
    return "Creates a new CSV file as a filtered, column-renamed copy of an existing CSV. "
        + "sourceColumns[i] maps to targetColumns[i] (same length required). "
        + "Optionally limit the number of data rows copied. "
        + "Fails if the target already exists. "
        + "Paths are relative to the sandbox root.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("source", "Relative path to the source CSV file", "string", true),
        new ToolProperty("target", "Relative path for the new output CSV file", "string", true),
        new ToolProperty(
            "sourceColumns",
            "Source column names to copy (in desired output order)",
            "array",
            true),
        new ToolProperty(
            "targetColumns",
            "Output header names corresponding to each sourceColumn (same length)",
            "array",
            true),
        new ToolProperty(
            "limit",
            "Maximum number of data rows to copy; omit or 0 for all rows",
            "integer",
            false));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var sourceRaw = params.get("source");
    var targetRaw = params.get("target");
    if (sourceRaw == null || sourceRaw.toString().isBlank()) {
      return "Error: source parameter is required.";
    }
    if (targetRaw == null || targetRaw.toString().isBlank()) {
      return "Error: target parameter is required.";
    }
    var srcColsRaw = params.get("sourceColumns");
    var tgtColsRaw = params.get("targetColumns");
    if (!(srcColsRaw instanceof List<?> srcList) || srcList.isEmpty()) {
      return "Error: sourceColumns must be a non-empty list.";
    }
    if (!(tgtColsRaw instanceof List<?> tgtList) || tgtList.isEmpty()) {
      return "Error: targetColumns must be a non-empty list.";
    }
    var sourceColumns = ((List<?>) srcColsRaw).stream().map(Object::toString).toList();
    var targetColumns = ((List<?>) tgtColsRaw).stream().map(Object::toString).toList();
    if (sourceColumns.size() != targetColumns.size()) {
      return "Error: sourceColumns and targetColumns must have the same length.";
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
      var resolvedSource = sandbox.resolve(sourceRaw.toString());
      var resolvedTarget = sandbox.resolve(targetRaw.toString());
      if (!Files.exists(resolvedSource)) {
        return "Error: file not found: " + sourceRaw;
      }
      if (Files.exists(resolvedTarget)) {
        return "Error: target already exists: " + targetRaw;
      }
      var rows = csvSupport.readRows(resolvedSource, sourceColumns, limit);
      var outputRows =
          rows.values().stream()
              .map(row -> sourceColumns.stream().map(col -> row.getOrDefault(col, "")).toList())
              .toList();
      Files.createDirectories(resolvedTarget.getParent());
      csvSupport.write(resolvedTarget, targetColumns, outputRows);
      return "Created " + targetRaw + " with " + outputRows.size() + " data rows.";
    } catch (SandboxAccessException e) {
      return "Error: " + e.getMessage();
    } catch (NoSuchFileException e) {
      return "Error: file not found: " + sourceRaw;
    } catch (IllegalArgumentException e) {
      return "Error: " + e.getMessage();
    } catch (IOException e) {
      return "Error: " + e.getMessage();
    }
  }
}
