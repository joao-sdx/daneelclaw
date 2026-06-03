package org.daneel.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ToolSelector {

  private static final String INSTRUCTIONS =
      """
      Tu es un classificateur d'outils. On te donne un catalogue d'outils disponibles et \
      un extrait de conversation. Retourne un tableau JSON contenant uniquement les noms \
      des outils pertinents pour répondre au dernier message de l'utilisateur. \
      Retourne [] si aucun outil n'est nécessaire (ex: conversation simple). \
      Retourne UNIQUEMENT le tableau JSON, sans texte supplémentaire.

      Catalogue des outils disponibles :
      """;

  private final ChatClient selectorClient;
  private final ToolRegistrar registrar;
  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final int historyWindow;

  public ToolSelector(
      @Qualifier("toolSelectorChatClient") ChatClient selectorClient,
      ToolRegistrar registrar,
      ObjectMapper objectMapper,
      @Value("${daneel.tools.select.enabled:true}") boolean enabled,
      @Value("${daneel.tools.select.history-window:4}") int historyWindow) {
    this.selectorClient = selectorClient;
    this.registrar = registrar;
    this.objectMapper = objectMapper;
    this.enabled = enabled;
    this.historyWindow = historyWindow;
  }

  public ToolCallback[] select(List<Message> sessionMessages) {
    if (!enabled) {
      return registrar.getCallbacks();
    }

    var catalog = registrar.catalog();
    if (catalog.isEmpty()) {
      return new ToolCallback[0];
    }

    var catalogText = buildCatalogText(catalog);
    var transcript = buildTranscript(sessionMessages);

    try {
      var response =
          selectorClient
              .prompt()
              .system(INSTRUCTIONS + catalogText)
              .user(transcript)
              .call()
              .content();

      var names =
          parseNames(response != null ? response : "[]", catalog.keySet().stream().toList());
      var selected = registrar.getCallbacks(names);
      log.info("tool_select count={} names={}", selected.length, names);
      return selected;
    } catch (Exception ex) {
      log.warn("tool_select_failed fallback=all message={}", ex.getMessage());
      return registrar.getCallbacks();
    }
  }

  private String buildCatalogText(java.util.Map<String, String> catalog) {
    var sb = new StringBuilder();
    for (var entry : catalog.entrySet()) {
      sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
    }
    return sb.toString();
  }

  private String buildTranscript(List<Message> sessionMessages) {
    var window =
        sessionMessages.size() > historyWindow
            ? sessionMessages.subList(
                sessionMessages.size() - historyWindow, sessionMessages.size())
            : sessionMessages;
    var sb = new StringBuilder();
    for (var msg : window) {
      sb.append(role(msg)).append(": ").append(msg.getText()).append('\n');
    }
    return sb.toString();
  }

  private List<String> parseNames(String response, List<String> knownNames) {
    // Try to extract a JSON array from the response
    var start = response.indexOf('[');
    var end = response.lastIndexOf(']');
    if (start >= 0 && end > start) {
      try {
        List<String> parsed =
            objectMapper.readValue(response.substring(start, end + 1), new TypeReference<>() {});
        var filtered = parsed.stream().filter(knownNames::contains).toList();
        if (!filtered.isEmpty() || parsed.isEmpty()) {
          return filtered;
        }
      } catch (Exception ignored) {
        // fall through to substring scan
      }
    }

    // Substring fallback: return any known tool name found in response text
    return knownNames.stream().filter(response::contains).toList();
  }

  private String role(Message msg) {
    if (msg instanceof UserMessage) {
      return "Utilisateur";
    }
    if (msg instanceof AssistantMessage) {
      return "Assistant";
    }
    return "Système";
  }
}
