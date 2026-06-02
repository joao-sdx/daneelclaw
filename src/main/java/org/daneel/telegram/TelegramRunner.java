package org.daneel.telegram;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.chat.ChatService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramRunner {

  static final int MAX_CHUNK = 4096;

  private final ChatService chatService;

  public List<String> handle(long chatId, String text) {
    log.info("telegram_handle chatId={} textLen={}", chatId, text.length());
    var sessionId = "tg-" + chatId;
    var response = chatService.chat(sessionId, text);
    return chunk(response.message());
  }

  private List<String> chunk(String text) {
    if (text.length() <= MAX_CHUNK) {
      return List.of(text);
    }
    var chunks = new ArrayList<String>();
    var pos = 0;
    while (pos < text.length()) {
      chunks.add(text.substring(pos, Math.min(pos + MAX_CHUNK, text.length())));
      pos += MAX_CHUNK;
    }
    return chunks;
  }
}
