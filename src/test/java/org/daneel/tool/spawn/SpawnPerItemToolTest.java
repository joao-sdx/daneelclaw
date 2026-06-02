package org.daneel.tool.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.camel.ProducerTemplate;
import org.daneel.task.FanOutItem;
import org.daneel.task.FanOutTracker;
import org.daneel.task.FanOutTracker.BatchSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpawnPerItemToolTest {

  @Mock private ProducerTemplate producerTemplate;
  @Mock private FanOutTracker tracker;

  private SpawnPerItemTool tool;

  @BeforeEach
  void setUp() {
    tool = new SpawnPerItemTool(producerTemplate, tracker, 30000L);
  }

  @Test
  void execute_fastBatch_returnsSummary() {
    when(tracker.awaitCompletion(any(), anyLong())).thenReturn(true);
    when(tracker.snapshot(any())).thenReturn(new BatchSnapshot(2, 2, 0, true));

    var result = tool.execute(Map.of("prompt", "Handle {item}", "items", List.of("a", "b")));

    assertThat(result).contains("2 handled");
    assertThat(result).contains("Processed 2 items");
  }

  @Test
  void execute_timeoutBatch_returnsPollMessage() {
    when(tracker.awaitCompletion(any(), anyLong())).thenReturn(false);
    when(tracker.snapshot(any())).thenReturn(new BatchSnapshot(2, 1, 0, false));

    var result = tool.execute(Map.of("prompt", "Handle {item}", "items", List.of("a", "b")));

    assertThat(result).contains("batch-");
    assertThat(result).contains("fanout_status");
  }

  @Test
  void execute_emptyItems_returnsError() {
    var result = tool.execute(Map.of("prompt", "Handle {item}", "items", List.of()));

    assertThat(result).startsWith("Error:");
  }

  @Test
  void execute_missingPromptPlaceholder_returnsError() {
    var result = tool.execute(Map.of("prompt", "Handle something", "items", List.of("a")));

    assertThat(result).startsWith("Error:");
    assertThat(result).contains("{item}");
  }

  @Test
  void execute_capturesFanOutItemsWithCorrectBatchId() {
    when(tracker.awaitCompletion(any(), anyLong())).thenReturn(true);
    when(tracker.snapshot(any())).thenReturn(new BatchSnapshot(2, 2, 0, true));
    var captor = ArgumentCaptor.forClass(FanOutItem.class);

    tool.execute(Map.of("prompt", "Handle {item}", "items", List.of("x", "y")));

    verify(tracker).start(startsWith("batch-"), eq(2));
    verify(producerTemplate, times(2)).sendBody(eq("seda:fanout"), captor.capture());
    var items = captor.getAllValues();
    assertThat(items).hasSize(2);
    assertThat(items.get(0).batchId()).isEqualTo(items.get(1).batchId());
    assertThat(items.get(0).batchId()).startsWith("batch-");
    assertThat(items.get(0).prompt()).contains("x");
    assertThat(items.get(1).prompt()).contains("y");
  }
}
