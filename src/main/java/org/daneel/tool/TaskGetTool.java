package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskGetTool implements DaneelToolInterface {

    private final TaskStore taskStore;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "task_get";
    }

    @Override
    public String description() {
        return "Gets a single planned task by its id. Returns the task as JSON, or an error if not found.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(new ToolProperty("id", "The task id", "string", true));
    }

    @Override
    public String execute(Map<String, Object> params) {
        var raw = params.get("id");
        if (raw == null || raw.toString().isBlank()) {
            return "Error: id parameter is required.";
        }
        return taskStore.findById(raw.toString())
                .map(task -> {
                    try {
                        return objectMapper.writeValueAsString(task);
                    } catch (Exception e) {
                        log.error("task_get_serialize_error id={}", raw, e);
                        return "Error: failed to serialize task.";
                    }
                })
                .orElse("Error: task not found.");
    }
}
