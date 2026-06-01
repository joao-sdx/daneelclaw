package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreateTool implements DaneelToolInterface {

    private final TaskStore taskStore;
    private final PromptCatalog promptCatalog;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "task_create";
    }

    @Override
    public String description() {
        return "Creates a new planned task using an existing prompt file. "
                + "Use prompt_list to discover available prompt files.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(
                new ToolProperty("name", "Human-readable task name", "string", true),
                new ToolProperty("promptFile",
                        "Filename of an existing prompt file (use prompt_list to see options)",
                        "string", true),
                new ToolProperty("nextRunAt",
                        "When to run the task as an ISO-8601 UTC instant, e.g. 2026-06-02T15:00:00Z",
                        "string", true),
                new ToolProperty("recurringIntervalMinutes",
                        "Recurrence interval in minutes; omit for a one-shot task",
                        "integer", false),
                new ToolProperty("enabled",
                        "Whether the task is active (default true)",
                        "boolean", false)
        );
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        var nameRaw = params.get("name");
        if (nameRaw == null || nameRaw.toString().isBlank()) {
            return "Error: name is required.";
        }
        var promptFileRaw = params.get("promptFile");
        if (promptFileRaw == null || promptFileRaw.toString().isBlank()) {
            return "Error: promptFile is required.";
        }
        if (!promptCatalog.exists(promptFileRaw.toString())) {
            return "Error: prompt file not found: " + promptFileRaw
                    + ". Use prompt_list to see available prompts.";
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

        var interval = parseInterval(params.get("recurringIntervalMinutes"));
        if (interval instanceof String error) {
            return error;
        }

        var enabled = parseEnabled(params.get("enabled"));
        var task = new PlannedTask(UUID.randomUUID().toString(), nameRaw.toString(),
                promptFileRaw.toString(), nextRunAt, (Integer) interval, enabled);
        taskStore.save(task);
        log.info("task_created_by_llm id={} name={}", task.id(), task.name());
        return objectMapper.writeValueAsString(task);
    }

    private Object parseInterval(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        var s = raw.toString().trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return "Error: recurringIntervalMinutes must be an integer.";
        }
    }

    private boolean parseEnabled(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw != null) {
            return Boolean.parseBoolean(raw.toString());
        }
        return true;
    }
}
