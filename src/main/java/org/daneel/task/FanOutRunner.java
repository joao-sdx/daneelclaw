package org.daneel.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Body;
import org.daneel.chat.ChatService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FanOutRunner {

  private final ChatService chatService;
  private final FanOutTracker tracker;

  public void run(@Body FanOutItem item) {
    log.info("fanout_starting session={}", item.sessionId());
    try {
      chatService.chat(item.sessionId(), item.prompt());
      tracker.recordSuccess(item.batchId());
      log.info("fanout_completed session={}", item.sessionId());
    } catch (Exception ex) {
      log.error("fanout_item_failed session={}", item.sessionId(), ex);
      tracker.recordFailure(item.batchId());
    }
  }
}
