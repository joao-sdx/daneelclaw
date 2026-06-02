package org.daneel.task;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

@Component
public class FanOutTracker {

  public record BatchSnapshot(int total, int handled, int failed, boolean done) {}

  private record Batch(
      int total, AtomicInteger handled, AtomicInteger failed, CountDownLatch latch) {}

  private final ConcurrentHashMap<String, Batch> batches = new ConcurrentHashMap<>();

  public void start(String batchId, int total) {
    batches.put(
        batchId,
        new Batch(total, new AtomicInteger(0), new AtomicInteger(0), new CountDownLatch(total)));
  }

  public void recordSuccess(String batchId) {
    var batch = batches.get(batchId);
    if (batch != null) {
      batch.handled().incrementAndGet();
      batch.latch().countDown();
    }
  }

  public void recordFailure(String batchId) {
    var batch = batches.get(batchId);
    if (batch != null) {
      batch.failed().incrementAndGet();
      batch.latch().countDown();
    }
  }

  @SneakyThrows
  public boolean awaitCompletion(String batchId, long timeoutMs) {
    var batch = batches.get(batchId);
    if (batch == null) {
      return false;
    }
    return batch.latch().await(timeoutMs, TimeUnit.MILLISECONDS);
  }

  public BatchSnapshot snapshot(String batchId) {
    var batch = batches.get(batchId);
    if (batch == null) {
      return null;
    }
    var handled = batch.handled().get();
    var failed = batch.failed().get();
    return new BatchSnapshot(batch.total(), handled, failed, handled + failed >= batch.total());
  }

  public void remove(String batchId) {
    batches.remove(batchId);
  }
}
