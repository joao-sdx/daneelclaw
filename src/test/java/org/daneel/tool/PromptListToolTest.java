package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.daneel.task.PromptCatalog;
import org.daneel.task.PromptSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptListToolTest {

  @Mock private PromptCatalog promptCatalog;

  private PromptListTool tool;

  @BeforeEach
  void setUp() {
    tool = new PromptListTool(promptCatalog, new ObjectMapper());
  }

  @Test
  void name_isPromptList() {
    assertThat(tool.name()).isEqualTo("prompt_list");
  }

  @Test
  void properties_isEmpty() {
    assertThat(tool.properties()).isEmpty();
  }

  @Test
  void execute_returnsJsonArrayWithSummaries() {
    when(promptCatalog.list())
        .thenReturn(List.of(new PromptSummary("hello.md", "Says bonjour via speak tool.")));
    var result = tool.execute(Map.of());
    assertThat(result).contains("hello.md").contains("Says bonjour via speak tool.");
  }

  @Test
  void execute_whenEmpty_returnsEmptyJsonArray() {
    when(promptCatalog.list()).thenReturn(List.of());
    assertThat(tool.execute(Map.of())).isEqualTo("[]");
  }
}
