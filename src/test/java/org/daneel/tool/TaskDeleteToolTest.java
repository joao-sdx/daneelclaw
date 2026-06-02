package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.daneel.task.PlannedTask;
import org.daneel.task.TaskRemovalService;
import org.daneel.task.TaskStore;
import org.daneel.tool.task.TaskDeleteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskDeleteToolTest {

  @Mock private TaskStore taskStore;
  @Mock private TaskRemovalService taskRemovalService;

  private TaskDeleteTool tool;

  @BeforeEach
  void setUp() {
    tool = new TaskDeleteTool(taskStore, taskRemovalService);
  }

  @Test
  void execute_deletesTask_returnsDone() {
    var task =
        new PlannedTask(
            "id1", "Task", "hello.md", Instant.parse("2026-06-02T10:00:00Z"), null, true);
    when(taskStore.findById("id1")).thenReturn(Optional.of(task));
    var result = tool.execute(Map.of("id", "id1"));
    verify(taskRemovalService).remove("id1");
    assertThat(result).isEqualTo("Deleted.");
  }

  @Test
  void execute_taskNotFound_returnsError() {
    when(taskStore.findById("x")).thenReturn(Optional.empty());
    assertThat(tool.execute(Map.of("id", "x"))).startsWith("Error:");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void name_isTaskDelete() {
    assertThat(tool.name()).isEqualTo("task_delete");
  }
}
