package org.daneel.tool.spawn;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.daneel.task.FanOutItem;
import org.daneel.task.FanOutTracker;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpawnPerItemTool implements DaneelToolInterface {

  private final ProducerTemplate producerTemplate;
  private final FanOutTracker tracker;
  private final long blockTimeoutMs;

  public SpawnPerItemTool(
      ProducerTemplate producerTemplate,
      FanOutTracker tracker,
      @Value("${daneel.tools.spawn.block-timeout-ms:30000}") long blockTimeoutMs) {
    this.producerTemplate = producerTemplate;
    this.tracker = tracker;
    this.blockTimeoutMs = blockTimeoutMs;
  }

  @Override
  public String name() {
    return "spawn_per_item";
  }

  @Override
  public String description() {
    return "Use this to LOOP or repeat an action over each item in a list (batch processing). "
        + "Spawns an independent sub-run for each item and blocks until all finish (or a timeout). "
        + "The prompt must contain {item}, which will be replaced with each item's value. "
        + "Each sub-run has its own isolated session with full tool access. "
        + "Returns a summary like 'Processed N items: H handled, F failed.' when done within the timeout. "
        + "If the timeout expires, returns a batch_id you can pass to fanout_status to check progress. "
        + "Do not call spawn_per_item inside a per-item prompt to avoid recursive fan-out.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "prompt",
            "Prompt template for each sub-run; must contain {item} as a placeholder for the item value",
            "string",
            true),
        new ToolProperty(
            "items", "List of items to process; one sub-run is spawned per item", "array", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var promptRaw = params.get("prompt");
    if (promptRaw == null || promptRaw.toString().isBlank()) {
      return "Error: prompt is required.";
    }
    var prompt = promptRaw.toString();

    var itemsRaw = params.get("items");
    if (!(itemsRaw instanceof List<?> rawList) || rawList.isEmpty()) {
      return "Error: items must be a non-empty list.";
    }

    if (!prompt.contains("{item}")) {
      return "Error: prompt must contain {item} as a placeholder for the item value.";
    }

    var now = Instant.now().toEpochMilli();
    var batchId = "batch-" + now;
    tracker.start(batchId, rawList.size());
    for (int i = 0; i < rawList.size(); i++) {
      var item = rawList.get(i);
      var msg = prompt.replace("{item}", item.toString());
      var sessionId = "fanout-" + now + "-" + i;
      log.info("fanout_enqueue session={} batch={}", sessionId, batchId);
      producerTemplate.sendBody("seda:fanout", new FanOutItem(batchId, sessionId, msg));
    }
    var done = tracker.awaitCompletion(batchId, blockTimeoutMs);
    var snap = tracker.snapshot(batchId);
    if (done) {
      tracker.remove(batchId);
      if (snap == null) {
        return "Batch " + batchId + " completed (results already collected).";
      }
      return "Processed "
          + snap.total()
          + " items: "
          + snap.handled()
          + " handled, "
          + snap.failed()
          + " failed.";
    } else {
      if (snap == null) {
        return "Batch " + batchId + " completed already (results collected concurrently).";
      }
      return "Batch "
          + batchId
          + " still running: "
          + (snap.handled() + snap.failed())
          + "/"
          + snap.total()
          + " done, "
          + snap.failed()
          + " failed so far. Call fanout_status with batch_id="
          + batchId
          + " to check progress.";
    }
  }
}
