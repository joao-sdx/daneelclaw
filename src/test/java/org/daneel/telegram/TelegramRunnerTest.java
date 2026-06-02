package org.daneel.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.daneel.chat.ChatResponse;
import org.daneel.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramRunnerTest {

  @Mock private ChatService chatService;

  private TelegramRunner runner;

  @BeforeEach
  void setUp() {
    runner = new TelegramRunner(chatService);
  }

  @Test
  void handle_buildsSessionIdFromChatId() {
    when(chatService.chat(eq("tg-12345"), eq("bonjour")))
        .thenReturn(new ChatResponse("Salut !", null));

    var chunks = runner.handle(12345L, "bonjour");

    var sessionCaptor = ArgumentCaptor.forClass(String.class);
    verify(chatService).chat(sessionCaptor.capture(), eq("bonjour"));
    assertThat(sessionCaptor.getValue()).isEqualTo("tg-12345");
    assertThat(chunks).containsExactly("Salut !");
  }

  @Test
  void handle_splitsReplyIntoChunksWhenOverLimit() {
    var longReply = "x".repeat(TelegramRunner.MAX_CHUNK + 1);
    when(chatService.chat(eq("tg-1"), eq("test"))).thenReturn(new ChatResponse(longReply, null));

    var chunks = runner.handle(1L, "test");

    assertThat(chunks).hasSize(2);
    assertThat(chunks.getFirst()).hasSize(TelegramRunner.MAX_CHUNK);
    assertThat(chunks.getLast()).hasSize(1);
  }

  @Test
  void handle_returnsExactlyOneChunkWhenReplyFitsWithinLimit() {
    var reply = "x".repeat(TelegramRunner.MAX_CHUNK);
    when(chatService.chat(eq("tg-99"), eq("ping"))).thenReturn(new ChatResponse(reply, null));

    var chunks = runner.handle(99L, "ping");

    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst()).hasSize(TelegramRunner.MAX_CHUNK);
  }
}
