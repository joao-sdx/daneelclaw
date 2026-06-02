package org.daneel.tool.spawn;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.daneel.task.FanOutItem;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpawnPerItemTool implements DaneelToolInterface {

  private final ProducerTemplate producerTemplate;

  @Override
  public String name() {
    return "spawn_per_item";
  }

  @Override
  public String description() {
    return "Spawns an independent background sub-run for each item in a list. "
        + "The prompt must contain {item}, which will be replaced with each item's value. "
        + "Each sub-run has its own isolated session with full tool access. "
        + "Returns immediately (fire-and-forget) — sub-runs execute sequentially in the background. "
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
    for (int i = 0; i < rawList.size(); i++) {
      var item = rawList.get(i);
      var msg = prompt.replace("{item}", item.toString());
      var sessionId = "fanout-" + now + "-" + i;
      log.info("fanout_enqueue session={}", sessionId);
      producerTemplate.sendBody("seda:fanout", new FanOutItem(sessionId, msg));
    }
    return "Spawned " + rawList.size() + " sub-runs.";
  }
}
