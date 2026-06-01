package org.daneel.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDeleteTool implements DaneelToolInterface {

    private final TaskStore taskStore;

    @Override
    public String name() {
        return "task_delete";
    }

    @Override
    public String description() {
        return "Deletes a planned task by id. The prompt file is not deleted.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(new ToolProperty("id", "The task id to delete", "string", true));
    }

    @Override
    public String execute(Map<String, Object> params) {
        var raw = params.get("id");
        if (raw == null || raw.toString().isBlank()) {
            return "Error: id is required.";
        }
        var id = raw.toString();
        if (taskStore.findById(id).isEmpty()) {
            return "Error: task not found: " + id;
        }
        taskStore.delete(id);
        log.info("task_deleted_by_llm id={}", id);
        return "Deleted.";
    }
}
