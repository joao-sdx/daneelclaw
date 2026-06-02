package org.daneel.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatService {

    private static final String CLEAR = "/clear";
    private static final String COMPACT = "/compact";
    private static final String SUMMARY_PREFIX = "Résumé de la conversation précédente :\n";

    private final Map<String, List<Message>> history = new ConcurrentHashMap<>();
    private final ChatClient chatClient;
    private final ChatClient summaryChatClient;
    private final int charThreshold;
    private final int keepLast;

    public ChatService(
            @Qualifier("chatClient") ChatClient chatClient,
            @Qualifier("summaryChatClient") ChatClient summaryChatClient,
            @Value("${daneel.chat.compact.char-threshold:8000}") int charThreshold,
            @Value("${daneel.chat.compact.keep-last-messages:4}") int keepLast) {
        this.chatClient = chatClient;
        this.summaryChatClient = summaryChatClient;
        this.charThreshold = charThreshold;
        this.keepLast = keepLast;
    }

    public ChatResponse chat(String sessionId, String userMessage) {
        var trimmed = userMessage.trim();

        if (trimmed.equalsIgnoreCase(CLEAR)) {
            history.remove(sessionId);
            log.info("chat_clear sessionId={}", sessionId);
            return new ChatResponse("Conversation réinitialisée.", "cleared");
        }

        var messages = history.computeIfAbsent(
                sessionId, k -> Collections.synchronizedList(new ArrayList<>()));

        if (trimmed.equalsIgnoreCase(COMPACT)) {
            var compacted = compact(messages);
            log.info("chat_compact_manual sessionId={} historySize={} compacted={}", sessionId, messages.size(), compacted);
            var msg = compacted ? "Conversation compactée." : "Rien à compacter.";
            return new ChatResponse(msg, "compacted");
        }

        if (totalChars(messages) > charThreshold) {
            compact(messages);
            log.info("chat_compact_auto sessionId={} historySize={}", sessionId, messages.size());
        }

        messages.add(new UserMessage(userMessage));
        var rawReply = chatClient.prompt().messages(messages).call().content();
        var reply = rawReply != null ? rawReply : "";
        messages.add(new AssistantMessage(reply));
        log.info("chat_service sessionId={} historySize={}", sessionId, messages.size());
        return new ChatResponse(reply, null);
    }

    private boolean compact(List<Message> messages) {
        if (messages.size() <= keepLast) {
            return false;
        }
        var splitAt = messages.size() - keepLast;
        var older = new ArrayList<>(messages.subList(0, splitAt));
        var tail = new ArrayList<>(messages.subList(splitAt, messages.size()));
        var transcript = older.stream()
                .map(m -> role(m) + ": " + m.getText())
                .collect(Collectors.joining("\n"));
        var summary = summaryChatClient.prompt().user(transcript).call().content();
        messages.clear();
        messages.add(new SystemMessage(SUMMARY_PREFIX + (summary != null ? summary : "")));
        messages.addAll(tail);
        return true;
    }

    private int totalChars(List<Message> messages) {
        return messages.stream().mapToInt(m -> m.getText().length()).sum();
    }

    private String role(Message m) {
        if (m instanceof UserMessage) {
            return "Utilisateur";
        }
        if (m instanceof AssistantMessage) {
            return "Assistant";
        }
        return "Système";
    }
}
