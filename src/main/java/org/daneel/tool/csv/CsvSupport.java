package org.daneel.tool.csv;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CsvSupport {

  private final CsvMapper csvMapper;

  public CsvSupport() {
    this.csvMapper = new CsvMapper();
    this.csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
  }

  public List<String> readHeaders(Path path) throws IOException {
    try (MappingIterator<String[]> it =
        csvMapper
            .readerFor(String[].class)
            .with(CsvSchema.emptySchema())
            .readValues(path.toFile())) {
      if (it.hasNext()) {
        return Arrays.asList(it.next());
      }
      return List.of();
    }
  }

  public LinkedHashMap<Integer, LinkedHashMap<String, String>> readRows(
      Path path, List<String> onlyColumns, int limit) throws IOException {
    try (MappingIterator<String[]> it =
        csvMapper
            .readerFor(String[].class)
            .with(CsvSchema.emptySchema())
            .readValues(path.toFile())) {
      if (!it.hasNext()) {
        return new LinkedHashMap<>();
      }
      var headers = Arrays.asList(it.next());
      if (onlyColumns != null) {
        for (var col : onlyColumns) {
          if (!headers.contains(col)) {
            throw new IllegalArgumentException("Unknown column: " + col);
          }
        }
      }
      var effectiveCols = onlyColumns != null ? onlyColumns : headers;
      var colIndex = new LinkedHashMap<String, Integer>();
      for (int i = 0; i < headers.size(); i++) {
        colIndex.put(headers.get(i), i);
      }
      var result = new LinkedHashMap<Integer, LinkedHashMap<String, String>>();
      int rowIdx = 0;
      while (it.hasNext() && (limit <= 0 || rowIdx < limit)) {
        var row = it.next();
        var rowMap = new LinkedHashMap<String, String>();
        for (var col : effectiveCols) {
          var idx = colIndex.get(col);
          rowMap.put(col, idx < row.length ? row[idx] : "");
        }
        result.put(rowIdx, rowMap);
        rowIdx++;
      }
      return result;
    }
  }

  public void write(Path path, List<String> headers, List<List<String>> rows) throws IOException {
    try (var sw =
        csvMapper
            .writerFor(String[].class)
            .with(CsvSchema.emptySchema())
            .writeValues(path.toFile())) {
      sw.write(headers.toArray(String[]::new));
      for (var row : rows) {
        sw.write(row.toArray(String[]::new));
      }
    }
  }

  public List<String> parseRecord(String csvLine) throws IOException {
    try (MappingIterator<String[]> it =
        csvMapper
            .readerFor(String[].class)
            .with(CsvSchema.emptySchema())
            .readValues(new StringReader(csvLine))) {
      if (it.hasNext()) {
        return Arrays.asList(it.next());
      }
      return List.of();
    }
  }

  // Caller must ensure file exists; CsvAppendTool validates via readHeaders before calling.
  public void appendRecord(Path path, List<String> values) throws IOException {
    var line =
        csvMapper
            .writerFor(String[].class)
            .with(CsvSchema.emptySchema())
            .writeValueAsString(values.toArray(String[]::new));
    var finalLine = line.endsWith("\n") ? line : line + "\n";
    Files.writeString(path, finalLine, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
  }
}
