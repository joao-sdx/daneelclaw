package org.daneel.task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.daneel.chat.ChatService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPoller {

  private final TaskStore taskStore;
  private final TaskRemovalService taskRemovalService;
  private final PromptResolver promptResolver;
  private final ChatService chatService;

  public List<PlannedTask> findDue(@ExchangeProperty("now") Instant now) {
    return taskStore.findAll().stream()
        .filter(PlannedTask::enabled)
        .filter(task -> !now.isBefore(task.nextRunAt()))
        .toList();
  }

  public void run(@Body PlannedTask task, @ExchangeProperty("now") Instant now) {
    try {
      var prompt = promptResolver.resolve(task, task.nextRunAt(), now);
      var sessionId = "auto-" + task.id() + "-" + task.nextRunAt().toEpochMilli();
      log.info("task_starting id={} name={}", task.id(), task.name());
      chatService.chat(sessionId, prompt);
      log.info("task_completed id={} name={}", task.id(), task.name());
    } finally {
      try {
        reschedule(task, now);
      } catch (Exception e) {
        log.error("task_reschedule_failed id={} name={}", task.id(), task.name(), e);
      }
    }
  }

  private void reschedule(PlannedTask task, Instant now) {
    if (task.recurringIntervalMinutes() == null || task.recurringIntervalMinutes() <= 0) {
      taskRemovalService.remove(task.id());
      return;
    }
    var next = task.nextRunAt().plus(task.recurringIntervalMinutes(), ChronoUnit.MINUTES);
    while (!next.isAfter(now)) {
      next = next.plus(task.recurringIntervalMinutes(), ChronoUnit.MINUTES);
    }
    taskStore.save(
        new PlannedTask(
            task.id(),
            task.name(),
            task.promptFile(),
            next,
            task.recurringIntervalMinutes(),
            task.enabled()));
  }
}
