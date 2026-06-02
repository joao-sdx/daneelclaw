package org.daneel.task;

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

  private FanOutRunner runner;

  @BeforeEach
  void setUp() {
    runner = new FanOutRunner(chatService);
  }

  @Test
  void run_delegatesToChatService() {
    var item = new FanOutItem("fanout-123-0", "Process this email: hello@example.com");

    runner.run(item);

    verify(chatService).chat("fanout-123-0", "Process this email: hello@example.com");
  }
}
