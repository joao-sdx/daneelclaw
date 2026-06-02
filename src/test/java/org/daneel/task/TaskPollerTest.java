package org.daneel.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.daneel.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskPollerTest {

  @Mock TaskStore taskStore;
  @Mock TaskRemovalService taskRemovalService;
  @Mock PromptResolver promptResolver;
  @Mock ChatService chatService;

  @InjectMocks TaskPoller poller;

  // Simulate server down from 14:00 to 17:30
  private static final Instant NOW = Instant.parse("2026-06-01T17:30:00Z");

  @BeforeEach
  void setUp() {
    lenient().when(promptResolver.resolve(any(), any(), any())).thenReturn("resolved prompt");
  }

  @Test
  void findDue_returnsDueTask() {
    when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, true)));

    var due = poller.findDue(NOW);

    assertThat(due).hasSize(1);
    assertThat(due.getFirst().id()).isEqualTo("t1");
  }

  @Test
  void findDue_excludesFutureTask() {
    when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T18:00:00Z", 60, true)));

    assertThat(poller.findDue(NOW)).isEmpty();
  }

  @Test
  void findDue_excludesDisabledTask() {
    when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, false)));

    assertThat(poller.findDue(NOW)).isEmpty();
  }

  @Test
  void run_executesTask() {
    var task = task("t1", "2026-06-01T14:00:00Z", 60, true);

    poller.run(task, NOW);

    verify(chatService).chat(startsWith("auto-t1-"), eq("resolved prompt"));
  }

  @Test
  void run_advancesRecurringTaskToNextFutureSlot() {
    // 14:00 + 4x60min = 18:00 is the first slot strictly after 17:30
    var task = task("t1", "2026-06-01T14:00:00Z", 60, true);

    poller.run(task, NOW);

    verify(taskStore)
        .save(
            argThat(
                t ->
                    t.id().equals("t1")
                        && t.nextRunAt().equals(Instant.parse("2026-06-01T18:00:00Z"))
                        && t.enabled()));
  }

  @Test
  void run_removesOneShotTaskAfterRun() {
    var task = task("t1", "2026-06-01T14:00:00Z", null, true);

    poller.run(task, NOW);

    verify(taskRemovalService).remove("t1");
    verify(taskStore, never()).save(argThat(t -> t.id().equals("t1")));
  }

  @Test
  void run_reschedulesEvenWhenExecutionFails() {
    var task = task("t1", "2026-06-01T14:00:00Z", 60, true);
    doThrow(new RuntimeException("LLM down")).when(chatService).chat(any(), any());

    assertThatThrownBy(() -> poller.run(task, NOW))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("LLM down");

    verify(taskStore).save(any(PlannedTask.class)); // reschedule still happens
  }

  private PlannedTask task(String id, String nextRunAt, Integer intervalMin, boolean enabled) {
    return new PlannedTask(
        id, "Task " + id, id + ".md", Instant.parse(nextRunAt), intervalMin, enabled);
  }
}
