package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.daneel.tool.error.ErrorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

@ExtendWith(MockitoExtension.class)
class ToolSelectorTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ChatClient selectorClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ToolRegistrar registrar;

  @BeforeEach
  void setUp() {
    var errorStore = mock(ErrorStore.class);
    var tool1 = new StubTool("file_read", "Read a file");
    var tool2 = new StubTool("file_write", "Write a file");
    var tool3 = new StubTool("speak", "Speak text aloud");
    registrar = new ToolRegistrar(List.of(tool1, tool2, tool3), objectMapper, errorStore);
  }

  @Test
  void select_jsonArrayResponse_returnsMatchingCallbacks() {
    when(selectorClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("[\"file_read\", \"file_write\"]");
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, true, 4);

    var callbacks = selector.select(List.of(new UserMessage("lis le fichier config.txt")));

    assertThat(callbacks).hasSize(2);
    assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("file_read");
    assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("file_write");
  }

  @Test
  void select_emptyJsonArray_returnsZeroCallbacks() {
    when(selectorClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("[]");
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, true, 4);

    var callbacks = selector.select(List.of(new UserMessage("comment ça va ?")));

    assertThat(callbacks).isEmpty();
  }

  @Test
  void select_jsonWrappedInText_extractsArray() {
    when(selectorClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("Sure, here are the tools: [\"speak\"] — that's my answer.");
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, true, 4);

    var callbacks = selector.select(List.of(new UserMessage("dis bonjour")));

    assertThat(callbacks).hasSize(1);
    assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("speak");
  }

  @Test
  void select_malformedJson_fallsBackToSubstringScan() {
    // response has no valid JSON array but contains a known tool name
    when(selectorClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("I would use file_write to accomplish this task.");
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, true, 4);

    var callbacks = selector.select(List.of(new UserMessage("écris dans un fichier")));

    assertThat(callbacks).hasSize(1);
    assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("file_write");
  }

  @Test
  void select_selectorThrows_fallsBackToAllTools() {
    when(selectorClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenThrow(new RuntimeException("LMStudio unreachable"));
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, true, 4);

    var callbacks = selector.select(List.of(new UserMessage("hello")));

    assertThat(callbacks).hasSize(3);
  }

  @Test
  void select_disabled_returnsAllTools() {
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, false, 4);

    var callbacks = selector.select(List.of(new UserMessage("hello")));

    assertThat(callbacks).hasSize(3);
  }

  @Test
  void select_unknownNamesFilteredOut() {
    // selector returns a name not in the catalog
    when(selectorClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("[\"file_read\", \"nonexistent_tool\"]");
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, true, 4);

    var callbacks = selector.select(List.of(new UserMessage("read something")));

    assertThat(callbacks).hasSize(1);
    assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("file_read");
  }

  @Test
  void select_historyWindowLimitsContext() {
    // 5 messages but window=2 — selector still works (no assertion on window here, just smoke)
    when(selectorClient.prompt().system(anyString()).user(anyString()).call().content())
        .thenReturn("[\"speak\"]");
    var selector = new ToolSelector(selectorClient, registrar, objectMapper, true, 2);
    List<Message> history =
        List.of(
            new UserMessage("msg1"),
            new UserMessage("msg2"),
            new UserMessage("msg3"),
            new UserMessage("msg4"),
            new UserMessage("say hello"));

    var callbacks = selector.select(history);

    assertThat(callbacks).hasSize(1);
  }

  private record StubTool(String name, String description) implements DaneelToolInterface {

    @Override
    public List<ToolProperty> properties() {
      return List.of();
    }

    @Override
    public String execute(Map<String, Object> params) {
      return "ok";
    }
  }
}
