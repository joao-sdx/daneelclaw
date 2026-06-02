package org.daneel.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FanOutTrackerTest {

  private FanOutTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new FanOutTracker();
  }

  @Test
  void start_thenAllSuccess_snapshotShowsDone() {
    tracker.start("b1", 2);
    tracker.recordSuccess("b1");
    tracker.recordSuccess("b1");

    var snap = tracker.snapshot("b1");

    assertThat(snap).isNotNull();
    assertThat(snap.done()).isTrue();
    assertThat(snap.handled()).isEqualTo(2);
    assertThat(snap.failed()).isEqualTo(0);
  }

  @Test
  void start_thenMixed_snapshotShowsCorrectCounts() {
    tracker.start("b2", 3);
    tracker.recordSuccess("b2");
    tracker.recordFailure("b2");
    tracker.recordSuccess("b2");

    var snap = tracker.snapshot("b2");

    assertThat(snap).isNotNull();
    assertThat(snap.done()).isTrue();
    assertThat(snap.handled()).isEqualTo(2);
    assertThat(snap.failed()).isEqualTo(1);
  }

  @Test
  void awaitCompletion_returnsTrueWhenDone() {
    tracker.start("b3", 1);
    tracker.recordSuccess("b3");

    var result = tracker.awaitCompletion("b3", 100);

    assertThat(result).isTrue();
  }

  @Test
  void awaitCompletion_returnsFalseOnTimeout() {
    tracker.start("b4", 1);

    var result = tracker.awaitCompletion("b4", 50);

    assertThat(result).isFalse();
  }

  @Test
  void snapshot_unknownBatch_returnsNull() {
    assertThat(tracker.snapshot("nonexistent")).isNull();
  }

  @Test
  void remove_dropsFromMap() {
    tracker.start("b5", 1);
    tracker.remove("b5");

    assertThat(tracker.snapshot("b5")).isNull();
  }

  @Test
  void recordSuccess_unknownBatch_isNoOp() {
    assertThatCode(() -> tracker.recordSuccess("nonexistent")).doesNotThrowAnyException();
  }

  @Test
  void recordFailure_unknownBatch_isNoOp() {
    assertThatCode(() -> tracker.recordFailure("nonexistent")).doesNotThrowAnyException();
  }
}
