package org.daneel.tool.error;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ErrorStore {

  private static final int MAX_SIZE = 500;

  private final long ttlMs;
  private final ConcurrentLinkedQueue<ToolError> errors = new ConcurrentLinkedQueue<>();

  public ErrorStore(@Value("${daneel.tools.errors.ttl-ms:300000}") long ttlMs) {
    this.ttlMs = ttlMs;
  }

  public void record(String toolName, String message) {
    if (errors.size() >= MAX_SIZE) {
      errors.poll();
    }
    errors.add(new ToolError(UUID.randomUUID().toString(), toolName, message, Instant.now()));
    log.debug("tool_error_recorded tool={} message={}", toolName, message);
  }

  public List<ToolError> drain() {
    var drained = new ArrayList<ToolError>();
    ToolError entry;
    while ((entry = errors.poll()) != null) {
      drained.add(entry);
    }
    return drained;
  }

  @Scheduled(fixedDelayString = "${daneel.tools.errors.sweep-interval-ms:60000}")
  public void sweepExpired() {
    var cutoff = Instant.now().minusMillis(ttlMs);
    errors.removeIf(e -> e.occurredAt().isBefore(cutoff));
    log.debug("tool_error_sweep cutoff={}", cutoff);
  }
}
