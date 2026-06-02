package org.daneel.task;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.daneel.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FanOutRunnerTest {

  @Mock private ChatService chatService;
  @Mock private FanOutTracker tracker;

  private FanOutRunner runner;

  @BeforeEach
  void setUp() {
    runner = new FanOutRunner(chatService, tracker);
  }

  @Test
  void run_success_recordsSuccess() {
    var item = new FanOutItem("batch-1", "fanout-123-0", "Process this email: hello@example.com");

    runner.run(item);

    verify(chatService).chat("fanout-123-0", "Process this email: hello@example.com");
    verify(tracker).recordSuccess("batch-1");
  }

  @Test
  void run_chatServiceThrows_recordsFailure() {
    var item = new FanOutItem("batch-1", "fanout-123-0", "Process this");
    doThrow(new RuntimeException("LLM error"))
        .when(chatService)
        .chat("fanout-123-0", "Process this");

    assertThatNoException().isThrownBy(() -> runner.run(item));

    verify(tracker).recordFailure("batch-1");
  }
}
