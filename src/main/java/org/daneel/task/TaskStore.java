package org.daneel.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TaskStore {

  private static final String TASKS_FILE = "tasks.yml";

  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final String tasksDir;

  public TaskStore(@Value("${daneel.scheduler.tasks-dir:./tasks}") String tasksDir) {
    this.tasksDir = tasksDir;
  }

  public synchronized List<PlannedTask> findAll() {
    var file = tasksFile();
    if (!file.exists()) {
      return new ArrayList<>();
    }
    return read(file);
  }

  public synchronized Optional<PlannedTask> findById(String id) {
    return findAll().stream().filter(t -> t.id().equals(id)).findFirst();
  }

  public synchronized void save(PlannedTask task) {
    var tasks = findAll();
    tasks.removeIf(t -> t.id().equals(task.id()));
    tasks.add(task);
    write(tasks);
  }

  public synchronized void delete(String id) {
    var tasks = findAll();
    tasks.removeIf(t -> t.id().equals(id));
    write(tasks);
  }

  @SneakyThrows
  private List<PlannedTask> read(File file) {
    var wrapper = YAML_MAPPER.readValue(file, TasksWrapper.class);
    return wrapper.tasks() != null ? new ArrayList<>(wrapper.tasks()) : new ArrayList<>();
  }

  @SneakyThrows
  private void write(List<PlannedTask> tasks) {
    var dir = Path.of(tasksDir);
    Files.createDirectories(dir);
    var tmp = dir.resolve("tasks.yml.tmp");
    var target = dir.resolve(TASKS_FILE);
    YAML_MAPPER.writeValue(tmp.toFile(), new TasksWrapper(tasks));
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private File tasksFile() {
    return Path.of(tasksDir, TASKS_FILE).toFile();
  }

  private record TasksWrapper(List<PlannedTask> tasks) {}
}
