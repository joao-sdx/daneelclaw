package org.daneel.tool.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.daneel.task.FanOutTracker;
import org.daneel.task.FanOutTracker.BatchSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FanOutStatusToolTest {

  @Mock private FanOutTracker tracker;

  private FanOutStatusTool tool;

  @BeforeEach
  void setUp() {
    tool = new FanOutStatusTool(tracker);
  }

  @Test
  void execute_unknownBatch_returnsUnknownMessage() {
    when(tracker.snapshot("batch-999")).thenReturn(null);

    var result = tool.execute(Map.of("batch_id", "batch-999"));

    assertThat(result).contains("Unknown");
    assertThat(result).contains("batch-999");
  }

  @Test
  void execute_inProgress_returnsProgressMessage() {
    when(tracker.snapshot("batch-1")).thenReturn(new BatchSnapshot(3, 1, 0, false));

    var result = tool.execute(Map.of("batch_id", "batch-1"));

    assertThat(result).contains("in progress");
    assertThat(result).contains("1/3");
  }

  @Test
  void execute_done_returnsCompleteAndRemoves() {
    when(tracker.snapshot("batch-2")).thenReturn(new BatchSnapshot(2, 2, 0, true));

    var result = tool.execute(Map.of("batch_id", "batch-2"));

    assertThat(result).contains("complete");
    assertThat(result).contains("2 handled");
    verify(tracker).remove("batch-2");
  }
}
