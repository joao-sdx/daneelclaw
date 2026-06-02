package org.daneel.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient chatClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient summaryChatClient;

  private ChatService chatService;

  // charThreshold=200, keepLast=2 — low values to test auto-compact in unit tests
  @BeforeEach
  void setUp() {
    chatService = new ChatService(chatClient, summaryChatClient, 200, 2);
    lenient().when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("AI reply");
    lenient()
        .when(summaryChatClient.prompt().user(anyString()).call().content())
        .thenReturn("SUMMARY");
    clearInvocations(summaryChatClient);
  }

  @Test
  void chat_returnsReplyWithNullAction() {
    var res = chatService.chat("s1", "hello");
    assertThat(res.message()).isEqualTo("AI reply");
    assertThat(res.action()).isNull();
  }

  @Test
  void chat_callsLlmForEachMessage() {
    chatService.chat("s1", "first");
    chatService.chat("s1", "second");
    verify(chatClient.prompt().messages(anyList()).call(), times(2)).content();
  }

  @Test
  void chat_isolatesSessionHistory() {
    chatService.chat("session-a", "hello");
    var res = chatService.chat("session-b", "hello");
    assertThat(res.message()).isEqualTo("AI reply");
  }

  @Test
  void chat_clearCommand_removesHistoryAndSignalsCleared() {
    chatService.chat("s1", "bonjour"); // seed history
    var res = chatService.chat("s1", "/clear");
    assertThat(res.action()).isEqualTo("cleared");
    assertThat(res.message()).isEqualTo("Conversation réinitialisée.");
    // after clear, next message should go to LLM with only 1 message in history
    chatService.chat("s1", "hi again");
    // verify the LLM was NOT called for the /clear itself but WAS called for "bonjour" and "hi
    // again" (2 total)
    verify(chatClient.prompt().messages(anyList()).call(), times(2)).content();
  }

  @Test
  void chat_compactCommand_replacesOldHistoryWithSummary() {
    // seed 3 turns (6 messages: 3 user + 3 assistant) so size (6) > keepLast (2)
    chatService.chat("s1", "msg1");
    chatService.chat("s1", "msg2");
    chatService.chat("s1", "msg3");
    var res = chatService.chat("s1", "/compact");
    assertThat(res.action()).isEqualTo("compacted");
    assertThat(res.message()).isEqualTo("Conversation compactée.");
    // summaryChatClient was called once
    verify(summaryChatClient.prompt().user(anyString()).call(), times(1)).content();
  }

  @Test
  void chat_normalMessage_underThreshold_doesNotCompact() {
    chatService.chat("s1", "salut");
    verifyNoInteractions(summaryChatClient);
  }

  @Test
  void chat_autoCompacts_whenCharThresholdExceeded() {
    // threshold is 200 chars. Seed two large messages to exceed it.
    // Each AI reply is "AI reply" (8 chars). User messages below are long.
    var bigMsg = "x".repeat(120);
    chatService.chat("s1", bigMsg); // adds user(120) + assistant(8) = 128 chars total
    chatService.chat("s1", bigMsg); // adds user(120) + assistant(8) = 256 chars → exceeds 200
    // On the third call, the pre-flight check fires auto-compact before the user message is
    // appended
    chatService.chat("s1", "normal");
    verify(summaryChatClient.prompt().user(anyString()).call(), times(1)).content();
  }
}
