package org.daneel.chat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChatService chatService;

  @Test
  void postChat_returnsServiceReply() throws Exception {
    when(chatService.chat("s1", "hello")).thenReturn(new ChatResponse("Hi there", null));

    mockMvc
        .perform(
            post("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"message\":\"hello\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Hi there"));
  }

  @Test
  void postChat_returns400ForEmptyMessage() throws Exception {
    mockMvc
        .perform(
            post("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\",\"message\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
