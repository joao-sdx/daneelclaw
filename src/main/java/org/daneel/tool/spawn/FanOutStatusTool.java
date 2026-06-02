package org.daneel.tool.spawn;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.daneel.task.FanOutTracker;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FanOutStatusTool implements DaneelToolInterface {

  private final FanOutTracker tracker;

  @Override
  public String name() {
    return "fanout_status";
  }

  @Override
  public String description() {
    return "Checks the status of a fan-out batch that is still running. "
        + "Pass the batch_id returned by spawn_per_item when it timed out. "
        + "Returns progress or a completion summary if the batch has finished.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty("batch_id", "The batch ID returned by spawn_per_item", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("batch_id");
    if (id == null) {
      return "Error: batch_id is required.";
    }
    var snap = tracker.snapshot(id.toString());
    if (snap == null) {
      return "Unknown or already-completed batch " + id + ".";
    }
    if (snap.done()) {
      tracker.remove(id.toString());
      return "Batch "
          + id
          + " complete: "
          + snap.handled()
          + " handled, "
          + snap.failed()
          + " failed of "
          + snap.total()
          + ".";
    }
    return "Batch "
        + id
        + " in progress: "
        + (snap.handled() + snap.failed())
        + "/"
        + snap.total()
        + " done, "
        + snap.failed()
        + " failed so far.";
  }
}
