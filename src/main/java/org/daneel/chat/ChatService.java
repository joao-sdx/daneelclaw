package org.daneel.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final Map<String, List<Message>> history = new ConcurrentHashMap<>();

    public String chat(String sessionId, String userMessage) {
        var messages = history.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        messages.add(new UserMessage(userMessage));
        log.info("chat_service sessionId={} historySize={}", sessionId, messages.size());
        var rawReply = chatClient.prompt()
                .messages(messages)
                .call()
                .content();
        var reply = rawReply != null ? rawReply : "";
        messages.add(new AssistantMessage(reply));
        return reply;
    }
}
