package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskListTool implements DaneelToolInterface {

  private final TaskStore taskStore;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "task_list";
  }

  @Override
  public String description() {
    return "Lists all planned tasks. Returns a JSON array of task objects.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of();
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    return objectMapper.writeValueAsString(taskStore.findAll());
  }
}
