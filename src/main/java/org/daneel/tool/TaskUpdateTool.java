package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskUpdateTool implements DaneelToolInterface {

  private final TaskStore taskStore;
  private final PromptCatalog promptCatalog;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "task_update";
  }

  @Override
  public String description() {
    return "Updates an existing planned task by id. All fields are replaced.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("id", "The task id to update", "string", true),
        new ToolProperty("name", "Human-readable task name", "string", true),
        new ToolProperty("promptFile", "Filename of an existing prompt file", "string", true),
        new ToolProperty(
            "nextRunAt",
            "New run time as an ISO-8601 UTC instant, e.g. 2026-06-02T15:00:00Z",
            "string",
            true),
        new ToolProperty(
            "recurringIntervalMinutes",
            "Recurrence interval in minutes; omit for a one-shot task",
            "integer",
            false),
        new ToolProperty("enabled", "Whether the task is active", "boolean", true));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var idRaw = params.get("id");
    if (idRaw == null || idRaw.toString().isBlank()) {
      return "Error: id is required.";
    }
    var id = idRaw.toString();
    if (taskStore.findById(id).isEmpty()) {
      return "Error: task not found: " + id;
    }
    var nameRaw = params.get("name");
    if (nameRaw == null || nameRaw.toString().isBlank()) {
      return "Error: name is required.";
    }
    var promptFileRaw = params.get("promptFile");
    if (promptFileRaw == null || promptFileRaw.toString().isBlank()) {
      return "Error: promptFile is required.";
    }
    if (!promptCatalog.exists(promptFileRaw.toString())) {
      return "Error: prompt file not found: " + promptFileRaw;
    }
    var nextRunAtRaw = params.get("nextRunAt");
    if (nextRunAtRaw == null || nextRunAtRaw.toString().isBlank()) {
      return "Error: nextRunAt is required.";
    }
    Instant nextRunAt;
    try {
      nextRunAt = Instant.parse(nextRunAtRaw.toString());
    } catch (DateTimeParseException e) {
      return "Error: nextRunAt must be an ISO-8601 UTC instant, e.g. 2026-06-02T15:00:00Z.";
    }

    Integer interval = null;
    var intervalRaw = params.get("recurringIntervalMinutes");
    if (intervalRaw instanceof Number n) {
      interval = n.intValue();
    }

    boolean enabled = true;
    var enabledRaw = params.get("enabled");
    if (enabledRaw instanceof Boolean b) {
      enabled = b;
    } else if (enabledRaw != null) {
      enabled = Boolean.parseBoolean(enabledRaw.toString());
    }

    var updated =
        new PlannedTask(
            id, nameRaw.toString(), promptFileRaw.toString(), nextRunAt, interval, enabled);
    taskStore.save(updated);
    log.info("task_updated_by_llm id={}", id);
    return objectMapper.writeValueAsString(updated);
  }
}
