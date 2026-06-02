package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskUpdateToolTest {

  @Mock private TaskStore taskStore;

  @Mock private PromptCatalog promptCatalog;

  private TaskUpdateTool tool;

  @BeforeEach
  void setUp() {
    var objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    tool = new TaskUpdateTool(taskStore, promptCatalog, objectMapper);
  }

  private PlannedTask existingTask() {
    return new PlannedTask(
        "id1", "Old Name", "hello.md", Instant.parse("2026-06-01T10:00:00Z"), null, true);
  }

  @Test
  void execute_updatesTask_returnsJson() {
    when(taskStore.findById("id1")).thenReturn(Optional.of(existingTask()));
    when(promptCatalog.exists("hello.md")).thenReturn(true);
    var params =
        Map.<String, Object>of(
            "id", "id1",
            "name", "New Name",
            "promptFile", "hello.md",
            "nextRunAt", "2026-06-03T10:00:00Z",
            "enabled", true);
    var result = tool.execute(params);
    assertThat(result).contains("New Name").contains("id1");
  }

  @Test
  void execute_taskNotFound_returnsError() {
    when(taskStore.findById("missing")).thenReturn(Optional.empty());
    var params =
        Map.<String, Object>of(
            "id", "missing",
            "name", "Name",
            "promptFile", "hello.md",
            "nextRunAt", "2026-06-03T10:00:00Z",
            "enabled", true);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_unknownPromptFile_returnsError() {
    when(taskStore.findById("id1")).thenReturn(Optional.of(existingTask()));
    when(promptCatalog.exists("bad.md")).thenReturn(false);
    var params =
        Map.<String, Object>of(
            "id", "id1",
            "name", "Name",
            "promptFile", "bad.md",
            "nextRunAt", "2026-06-03T10:00:00Z",
            "enabled", true);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_badNextRunAt_returnsError() {
    when(taskStore.findById("id1")).thenReturn(Optional.of(existingTask()));
    when(promptCatalog.exists("hello.md")).thenReturn(true);
    var params =
        Map.<String, Object>of(
            "id", "id1",
            "name", "Name",
            "promptFile", "hello.md",
            "nextRunAt", "invalid",
            "enabled", true);
    assertThat(tool.execute(params)).startsWith("Error:");
  }
}
