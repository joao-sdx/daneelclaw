package org.daneel.task;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final TaskStore taskStore;
  private final TaskRemovalService taskRemovalService;

  @GetMapping
  public List<PlannedTask> list() {
    return taskStore.findAll();
  }

  @GetMapping("/{id}")
  public ResponseEntity<PlannedTask> get(@PathVariable String id) {
    return taskStore.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlannedTask create(@Valid @RequestBody TaskRequest request) {
    var task =
        new PlannedTask(
            UUID.randomUUID().toString(),
            request.name(),
            request.promptFile(),
            request.nextRunAt(),
            request.recurringIntervalMinutes(),
            request.enabled());
    taskStore.save(task);
    log.info("task_created id={} name={}", task.id(), task.name());
    return task;
  }

  @PutMapping("/{id}")
  public ResponseEntity<PlannedTask> update(
      @PathVariable String id, @Valid @RequestBody TaskRequest request) {
    return taskStore
        .findById(id)
        .map(
            existing -> {
              var updated =
                  new PlannedTask(
                      id,
                      request.name(),
                      request.promptFile(),
                      request.nextRunAt(),
                      request.recurringIntervalMinutes(),
                      request.enabled());
              taskStore.save(updated);
              log.info("task_updated id={}", id);
              return ResponseEntity.ok(updated);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    if (taskStore.findById(id).isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    taskRemovalService.remove(id);
    log.info("task_deleted id={}", id);
    return ResponseEntity.noContent().build();
  }
}
