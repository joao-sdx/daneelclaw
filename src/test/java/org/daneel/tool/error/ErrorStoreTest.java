package org.daneel.tool.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ErrorStoreTest {

  private ErrorStore store;

  @BeforeEach
  void setUp() {
    store = new ErrorStore(300_000L);
  }

  @Test
  void record_thenDrain_returnsRecordedError() {
    store.record("my_tool", "something broke");

    var drained = store.drain();

    assertThat(drained).hasSize(1);
    assertThat(drained.getFirst().toolName()).isEqualTo("my_tool");
    assertThat(drained.getFirst().message()).isEqualTo("something broke");
    assertThat(drained.getFirst().id()).isNotBlank();
    assertThat(drained.getFirst().occurredAt()).isNotNull();
  }

  @Test
  void drain_clearsTheStore() {
    store.record("my_tool", "first error");

    store.drain();
    var secondDrain = store.drain();

    assertThat(secondDrain).isEmpty();
  }

  @Test
  void drain_whenEmpty_returnsEmptyList() {
    assertThat(store.drain()).isEmpty();
  }

  @Test
  void record_multiple_drainReturnsAll() {
    store.record("tool_a", "error a");
    store.record("tool_b", "error b");
    store.record("tool_c", "error c");

    var drained = store.drain();

    assertThat(drained).hasSize(3);
    assertThat(drained.stream().map(ToolError::toolName))
        .containsExactlyInAnyOrder("tool_a", "tool_b", "tool_c");
  }

  @Test
  void sweepExpired_removesOldEntries() throws Exception {
    var shortTtlStore = new ErrorStore(1L);
    shortTtlStore.record("slow_tool", "stale error");

    Thread.sleep(10);
    shortTtlStore.sweepExpired();

    assertThat(shortTtlStore.drain()).isEmpty();
  }

  @Test
  void sweepExpired_keepsRecentEntries() {
    store.record("fresh_tool", "recent error");

    store.sweepExpired();

    assertThat(store.drain()).hasSize(1);
  }

  @Test
  void record_assignsUniqueIds() {
    store.record("tool_x", "error 1");
    store.record("tool_x", "error 2");

    var drained = store.drain();
    var ids = drained.stream().map(ToolError::id).toList();

    assertThat(ids).doesNotHaveDuplicates();
  }

  @Test
  void drain_preservesOccurredAt() {
    var before = Instant.now();
    store.record("timed_tool", "some error");
    var after = Instant.now();

    var error = store.drain().getFirst();

    assertThat(error.occurredAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
  }
}
