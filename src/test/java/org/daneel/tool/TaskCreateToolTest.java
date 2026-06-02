package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskCreateToolTest {

  @Mock private TaskStore taskStore;

  @Mock private PromptCatalog promptCatalog;

  private TaskCreateTool tool;

  @BeforeEach
  void setUp() {
    var objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    tool = new TaskCreateTool(taskStore, promptCatalog, objectMapper);
  }

  @Test
  void execute_createsAndReturnsTask() {
    when(promptCatalog.exists("hello.md")).thenReturn(true);
    var params =
        Map.<String, Object>of(
            "name", "My Task",
            "promptFile", "hello.md",
            "nextRunAt", "2026-06-02T10:00:00Z");
    var result = tool.execute(params);
    var captor = ArgumentCaptor.forClass(PlannedTask.class);
    verify(taskStore).save(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("My Task");
    assertThat(captor.getValue().enabled()).isTrue();
    assertThat(result).contains("My Task");
  }

  @Test
  void execute_missingName_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("name", null);
    params.put("promptFile", "hello.md");
    params.put("nextRunAt", "2026-06-02T10:00:00Z");
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_unknownPromptFile_returnsError() {
    when(promptCatalog.exists("missing.md")).thenReturn(false);
    var params =
        Map.<String, Object>of(
            "name", "Task",
            "promptFile", "missing.md",
            "nextRunAt", "2026-06-02T10:00:00Z");
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_badNextRunAt_returnsError() {
    when(promptCatalog.exists("hello.md")).thenReturn(true);
    var params =
        Map.<String, Object>of(
            "name", "Task",
            "promptFile", "hello.md",
            "nextRunAt", "not-a-date");
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_missingNextRunAt_returnsError() {
    when(promptCatalog.exists("hello.md")).thenReturn(true);
    var params = new HashMap<String, Object>();
    params.put("name", "Task");
    params.put("promptFile", "hello.md");
    params.put("nextRunAt", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }
}
