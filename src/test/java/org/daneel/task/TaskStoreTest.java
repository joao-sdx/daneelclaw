package org.daneel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskStoreTest {

  @TempDir Path tempDir;

  private TaskStore store;

  @BeforeEach
  void setUp() {
    store = new TaskStore(tempDir.toString());
  }

  @Test
  void findAll_emptyWhenFileDoesNotExist() {
    assertThat(store.findAll()).isEmpty();
  }

  @Test
  void save_andFindById() {
    var task = task("t1");
    store.save(task);
    assertThat(store.findById("t1")).contains(task);
  }

  @Test
  void save_persistsToDisk() {
    store.save(task("t1"));
    var store2 = new TaskStore(tempDir.toString());
    assertThat(store2.findById("t1")).isPresent();
  }

  @Test
  void save_replacesExistingTask() {
    store.save(task("t1"));
    var updated =
        new PlannedTask("t1", "Updated", "t1.md", Instant.parse("2026-06-01T15:00:00Z"), 60, true);
    store.save(updated);
    assertThat(store.findById("t1").map(PlannedTask::name)).contains("Updated");
    assertThat(store.findAll()).hasSize(1);
  }

  @Test
  void delete_removesTask() {
    store.save(task("t1"));
    store.delete("t1");
    assertThat(store.findById("t1")).isEmpty();
    assertThat(store.findAll()).isEmpty();
  }

  @Test
  void findAll_returnsAllTasks() {
    store.save(task("t1"));
    store.save(task("t2"));
    assertThat(store.findAll()).hasSize(2);
  }

  private PlannedTask task(String id) {
    return new PlannedTask(
        id, "Task " + id, id + ".md", Instant.parse("2026-06-01T14:00:00Z"), 60, true);
  }
}
