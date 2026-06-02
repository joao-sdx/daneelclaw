package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.daneel.tool.csv.CsvFilterCopyTool;
import org.daneel.tool.csv.CsvSupport;
import org.daneel.tool.file.SandboxFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvFilterCopyToolTest {

  @TempDir Path tempDir;

  private CsvFilterCopyTool tool;
  private CsvSupport csvSupport;

  @BeforeEach
  void setUp() {
    csvSupport = new CsvSupport();
    tool = new CsvFilterCopyTool(new SandboxFileSystem(tempDir.toString()), csvSupport);
  }

  @Test
  void execute_copiesSelectedColumnsAndRenamesThem() throws Exception {
    Files.writeString(
        tempDir.resolve("src.csv"),
        "id,name,email\n1,Ana,a@x.com\n2,Bob,b@x.com\n",
        StandardCharsets.UTF_8);
    var result =
        tool.execute(
            Map.of(
                "source",
                "src.csv",
                "target",
                "out.csv",
                "sourceColumns",
                List.of("name", "email"),
                "targetColumns",
                List.of("Nom", "Mail")));
    assertThat(result).doesNotStartWith("Error:");
    var headers = csvSupport.readHeaders(tempDir.resolve("out.csv"));
    assertThat(headers).containsExactly("Nom", "Mail");
    var rows = csvSupport.readRows(tempDir.resolve("out.csv"), null, 0);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("Nom", "Ana").containsEntry("Mail", "a@x.com");
  }

  @Test
  void execute_appliesRowLimit() throws Exception {
    Files.writeString(
        tempDir.resolve("src.csv"), "id,name\n1,Ana\n2,Bob\n3,Cam\n", StandardCharsets.UTF_8);
    tool.execute(
        Map.of(
            "source",
            "src.csv",
            "target",
            "out.csv",
            "sourceColumns",
            List.of("id"),
            "targetColumns",
            List.of("id"),
            "limit",
            "2"));
    var rows = csvSupport.readRows(tempDir.resolve("out.csv"), null, 0);
    assertThat(rows).hasSize(2);
  }

  @Test
  void execute_targetAlreadyExists_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("src.csv"), "id\n1\n", StandardCharsets.UTF_8);
    Files.createFile(tempDir.resolve("out.csv"));
    assertThat(
            tool.execute(
                Map.of(
                    "source",
                    "src.csv",
                    "target",
                    "out.csv",
                    "sourceColumns",
                    List.of("id"),
                    "targetColumns",
                    List.of("id"))))
        .startsWith("Error:");
  }

  @Test
  void execute_sourceNotFound_returnsError() {
    assertThat(
            tool.execute(
                Map.of(
                    "source",
                    "no-such.csv",
                    "target",
                    "out.csv",
                    "sourceColumns",
                    List.of("id"),
                    "targetColumns",
                    List.of("id"))))
        .startsWith("Error:");
  }

  @Test
  void execute_columnListsLengthMismatch_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("src.csv"), "id,name\n1,Ana\n", StandardCharsets.UTF_8);
    assertThat(
            tool.execute(
                Map.of(
                    "source",
                    "src.csv",
                    "target",
                    "out.csv",
                    "sourceColumns",
                    List.of("id", "name"),
                    "targetColumns",
                    List.of("id"))))
        .startsWith("Error:");
  }

  @Test
  void execute_unknownSourceColumn_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("src.csv"), "id,name\n1,Ana\n", StandardCharsets.UTF_8);
    assertThat(
            tool.execute(
                Map.of(
                    "source",
                    "src.csv",
                    "target",
                    "out.csv",
                    "sourceColumns",
                    List.of("nonexistent"),
                    "targetColumns",
                    List.of("x"))))
        .startsWith("Error:");
  }

  @Test
  void execute_traversalOnSource_returnsError() {
    assertThat(
            tool.execute(
                Map.of(
                    "source",
                    "../etc/passwd",
                    "target",
                    "out.csv",
                    "sourceColumns",
                    List.of("id"),
                    "targetColumns",
                    List.of("id"))))
        .startsWith("Error:");
  }

  @Test
  void execute_traversalOnTarget_returnsError() throws Exception {
    Files.writeString(tempDir.resolve("src.csv"), "id\n1\n", StandardCharsets.UTF_8);
    assertThat(
            tool.execute(
                Map.of(
                    "source",
                    "src.csv",
                    "target",
                    "../evil.csv",
                    "sourceColumns",
                    List.of("id"),
                    "targetColumns",
                    List.of("id"))))
        .startsWith("Error:");
  }
}
