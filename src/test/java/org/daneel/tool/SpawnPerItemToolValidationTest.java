package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.ProducerTemplate;
import org.daneel.task.FanOutItem;
import org.daneel.task.FanOutTracker;
import org.daneel.task.FanOutTracker.BatchSnapshot;
import org.daneel.tool.spawn.SpawnPerItemTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpawnPerItemToolValidationTest {

  @Mock private ProducerTemplate producerTemplate;
  @Mock private FanOutTracker tracker;

  private SpawnPerItemTool tool;

  @BeforeEach
  void setUp() {
    tool = new SpawnPerItemTool(producerTemplate, tracker, 30000L);
  }

  @Test
  void execute_spawnsOneSubRunPerItem() {
    when(tracker.awaitCompletion(any(), anyLong())).thenReturn(true);
    when(tracker.snapshot(any())).thenReturn(new BatchSnapshot(3, 3, 0, true));
    var params =
        Map.<String, Object>of(
            "prompt",
            "Process this email: {item}",
            "items",
            List.of("a@example.com", "b@example.com", "c@example.com"));

    var result = tool.execute(params);

    assertThat(result).contains("3");
    var captor = ArgumentCaptor.forClass(FanOutItem.class);
    verify(producerTemplate, times(3)).sendBody(eq("seda:fanout"), captor.capture());
    var fanOutItems = captor.getAllValues();
    assertThat(fanOutItems.get(0).prompt()).contains("a@example.com");
    assertThat(fanOutItems.get(1).prompt()).contains("b@example.com");
    assertThat(fanOutItems.get(2).prompt()).contains("c@example.com");
  }

  @Test
  void execute_sessionIdsHaveExpectedPattern() {
    when(tracker.awaitCompletion(any(), anyLong())).thenReturn(true);
    when(tracker.snapshot(any())).thenReturn(new BatchSnapshot(2, 2, 0, true));
    var params =
        Map.<String, Object>of("prompt", "Handle: {item}", "items", List.of("first", "second"));

    tool.execute(params);

    var captor = ArgumentCaptor.forClass(FanOutItem.class);
    verify(producerTemplate, times(2)).sendBody(eq("seda:fanout"), captor.capture());
    var fanOutItems = captor.getAllValues();
    assertThat(fanOutItems.get(0).sessionId()).startsWith("fanout-");
    assertThat(fanOutItems.get(0).sessionId()).endsWith("-0");
    assertThat(fanOutItems.get(1).sessionId()).endsWith("-1");
    assertThat(fanOutItems.get(0).sessionId()).isNotEqualTo(fanOutItems.get(1).sessionId());
  }

  @Test
  void execute_withoutItemPlaceholder_returnsError() {
    var params =
        Map.<String, Object>of("prompt", "Process the following", "items", List.of("widget"));

    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_blankPrompt_returnsError() {
    var params = Map.<String, Object>of("prompt", "  ", "items", List.of("x"));

    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_missingPrompt_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("items", List.of("x"));

    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_emptyItems_returnsError() {
    var params = Map.<String, Object>of("prompt", "Do {item}", "items", List.of());

    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_missingItems_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("prompt", "Do {item}");

    assertThat(tool.execute(params)).startsWith("Error:");
  }

  @Test
  void execute_nonListItems_returnsError() {
    var params = Map.<String, Object>of("prompt", "Do {item}", "items", "not-a-list");

    assertThat(tool.execute(params)).startsWith("Error:");
  }
}
