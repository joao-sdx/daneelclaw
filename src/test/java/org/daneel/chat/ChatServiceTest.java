package org.daneel.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatClient);
        lenient().when(chatClient.prompt().messages(anyList()).call().content())
                 .thenReturn("AI reply");
    }

    @Test
    void chat_returnsReply() {
        assertThat(chatService.chat("s1", "hello")).isEqualTo("AI reply");
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
        var reply = chatService.chat("session-b", "hello");
        assertThat(reply).isEqualTo("AI reply");
    }
}
