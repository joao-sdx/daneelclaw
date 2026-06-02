package org.daneel.task;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskRemovalServiceTest {

  @Mock TaskStore taskStore;
  @Mock PromptCatalog promptCatalog;

  @InjectMocks TaskRemovalService service;

  @Test
  void remove_deletesTaskFromStore() {
    when(taskStore.findById("t1")).thenReturn(Optional.empty());

    service.remove("t1");

    verify(taskStore).delete("t1");
  }

  @Test
  void remove_deletesAdhocPromptWhenUnreferenced() {
    var task = task("t1", "p1717000000000.md");
    when(taskStore.findById("t1")).thenReturn(Optional.of(task));
    when(promptCatalog.isAdhoc("p1717000000000.md")).thenReturn(true);
    when(taskStore.findAll()).thenReturn(List.of()); // no remaining tasks

    service.remove("t1");

    verify(promptCatalog).delete("p1717000000000.md");
  }

  @Test
  void remove_keepsAdhocPromptWhenStillReferenced() {
    var task = task("t1", "p1717000000000.md");
    var other = task("t2", "p1717000000000.md");
    when(taskStore.findById("t1")).thenReturn(Optional.of(task));
    when(promptCatalog.isAdhoc("p1717000000000.md")).thenReturn(true);
    when(taskStore.findAll()).thenReturn(List.of(other)); // another task still references it

    service.remove("t1");

    verify(promptCatalog, never()).delete(any());
  }

  @Test
  void remove_keepsNamedPrompt() {
    var task = task("t1", "hello.md");
    when(taskStore.findById("t1")).thenReturn(Optional.of(task));
    when(promptCatalog.isAdhoc("hello.md")).thenReturn(false);

    service.remove("t1");

    verify(promptCatalog, never()).delete(any());
    verify(taskStore, never()).findAll();
  }

  @Test
  void remove_whenTaskNotFound_neverTouchesPrompt() {
    when(taskStore.findById("missing")).thenReturn(Optional.empty());

    service.remove("missing");

    verify(taskStore).delete("missing");
    verify(promptCatalog, never()).isAdhoc(any());
    verify(promptCatalog, never()).delete(any());
  }

  private PlannedTask task(String id, String promptFile) {
    return new PlannedTask(
        id, "Task " + id, promptFile, Instant.parse("2026-06-01T14:00:00Z"), null, true);
  }
}
