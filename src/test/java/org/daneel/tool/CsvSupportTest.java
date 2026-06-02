package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.daneel.tool.csv.CsvSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvSupportTest {

  @TempDir Path tempDir;

  private CsvSupport csvSupport;

  @BeforeEach
  void setUp() {
    csvSupport = new CsvSupport();
  }

  @Test
  void readHeaders_returnColumnNames() throws Exception {
    var csv = tempDir.resolve("data.csv");
    Files.writeString(csv, "id,name,email\n1,Ana,a@x.com\n", StandardCharsets.UTF_8);
    assertThat(csvSupport.readHeaders(csv)).containsExactly("id", "name", "email");
  }

  @Test
  void readHeaders_emptyFile_returnsEmptyList() throws Exception {
    var csv = tempDir.resolve("empty.csv");
    Files.writeString(csv, "", StandardCharsets.UTF_8);
    assertThat(csvSupport.readHeaders(csv)).isEmpty();
  }

  @Test
  void readRows_allColumns_returnsAllRows() throws Exception {
    var csv = tempDir.resolve("data.csv");
    Files.writeString(csv, "id,name\n1,Ana\n2,Bob\n", StandardCharsets.UTF_8);
    var rows = csvSupport.readRows(csv, null, 0);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("id", "1").containsEntry("name", "Ana");
    assertThat(rows.get(1)).containsEntry("id", "2").containsEntry("name", "Bob");
  }

  @Test
  void readRows_selectedColumns_returnsOnlyThoseColumns() throws Exception {
    var csv = tempDir.resolve("data.csv");
    Files.writeString(csv, "id,name,email\n1,Ana,a@x.com\n", StandardCharsets.UTF_8);
    var rows = csvSupport.readRows(csv, List.of("name", "email"), 0);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsOnlyKeys("name", "email");
    assertThat(rows.get(0)).containsEntry("name", "Ana").containsEntry("email", "a@x.com");
  }

  @Test
  void readRows_withLimit_returnsAtMostLimitRows() throws Exception {
    var csv = tempDir.resolve("data.csv");
    Files.writeString(csv, "id,name\n1,Ana\n2,Bob\n3,Cam\n", StandardCharsets.UTF_8);
    var rows = csvSupport.readRows(csv, null, 2);
    assertThat(rows).hasSize(2);
  }

  @Test
  void readRows_unknownColumn_throwsIllegalArgumentException() throws Exception {
    var csv = tempDir.resolve("data.csv");
    Files.writeString(csv, "id,name\n1,Ana\n", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> csvSupport.readRows(csv, List.of("nonexistent"), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  void write_createsValidCsv() throws Exception {
    var csv = tempDir.resolve("out.csv");
    csvSupport.write(csv, List.of("name", "email"), List.of(List.of("Ana", "a@x.com")));
    var rows = csvSupport.readRows(csv, null, 0);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("name", "Ana").containsEntry("email", "a@x.com");
  }

  @Test
  void write_handlesCommaInsideValue() throws Exception {
    var csv = tempDir.resolve("out.csv");
    csvSupport.write(csv, List.of("name"), List.of(List.of("Smith, Ana")));
    var rows = csvSupport.readRows(csv, null, 0);
    assertThat(rows.get(0)).containsEntry("name", "Smith, Ana");
  }

  @Test
  void parseRecord_splitsPlainLine() throws Exception {
    assertThat(csvSupport.parseRecord("a,b,c")).containsExactly("a", "b", "c");
  }

  @Test
  void parseRecord_handlesQuotedField() throws Exception {
    assertThat(csvSupport.parseRecord("\"Smith, Ana\",b")).containsExactly("Smith, Ana", "b");
  }

  @Test
  void appendRecord_nonExistentFile_throwsException() {
    var csv = tempDir.resolve("nonexistent.csv");
    assertThatThrownBy(() -> csvSupport.appendRecord(csv, List.of("1", "Ana")))
        .isInstanceOf(java.nio.file.NoSuchFileException.class);
  }

  @Test
  void appendRecord_appendsRowToExistingFile() throws Exception {
    var csv = tempDir.resolve("data.csv");
    Files.writeString(csv, "id,name\n1,Ana\n", StandardCharsets.UTF_8);
    csvSupport.appendRecord(csv, List.of("2", "Bob"));
    var rows = csvSupport.readRows(csv, null, 0);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(1)).containsEntry("id", "2").containsEntry("name", "Bob");
  }
}
