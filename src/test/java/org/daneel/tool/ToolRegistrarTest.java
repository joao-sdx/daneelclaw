package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.daneel.tool.error.ErrorStore;
import org.junit.jupiter.api.Test;

class ToolRegistrarTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ErrorStore errorStore = mock(ErrorStore.class);

  @Test
  void getCallbacks_returnsOneCallbackPerTool() {
    var tool1 = new StubTool("tool_one", "Tool One", List.of(), "result_one");
    var tool2 = new StubTool("tool_two", "Tool Two", List.of(), "result_two");
    var registrar = new ToolRegistrar(List.of(tool1, tool2), objectMapper, errorStore);

    var callbacks = registrar.getCallbacks();

    assertThat(callbacks).hasSize(2);
    assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tool_one");
    assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("tool_two");
  }

  @Test
  void getCallbacks_callDelegatesExecute() throws Exception {
    var tool = new StubTool("my_tool", "My tool", List.of(), "hello");
    var registrar = new ToolRegistrar(List.of(tool), objectMapper, errorStore);

    var result = registrar.getCallbacks()[0].call("{}");

    assertThat(result).isEqualTo("hello");
  }

  @Test
  void getCallbacks_emptyTools_returnsEmptyArray() {
    var registrar = new ToolRegistrar(List.of(), objectMapper, errorStore);

    assertThat(registrar.getCallbacks()).isEmpty();
  }

  @Test
  void getCallbacks_schemaIncludesRequiredProperty() {
    var prop = new ToolProperty("timezone", "IANA timezone", "string", true);
    var tool = new StubTool("tz_tool", "TZ tool", List.of(prop), "UTC");
    var registrar = new ToolRegistrar(List.of(tool), objectMapper, errorStore);

    var schema = registrar.getCallbacks()[0].getToolDefinition().inputSchema();

    assertThat(schema).contains("\"timezone\"");
    assertThat(schema).contains("[\"timezone\"]");
  }

  @Test
  void getCallbacks_arrayPropertyIncludesItemsDefinition() {
    var prop = new ToolProperty("items", "List of items", "array", true);
    var tool = new StubTool("fan_tool", "Fan tool", List.of(prop), "ok");
    var registrar = new ToolRegistrar(List.of(tool), objectMapper, errorStore);

    var schema = registrar.getCallbacks()[0].getToolDefinition().inputSchema();

    assertThat(schema).contains("\"type\":\"array\"");
    assertThat(schema).contains("\"items\"");
    assertThat(schema).contains("\"type\":\"string\"");
  }

  @Test
  void catalog_returnsAllNamesAndDescriptions() {
    var tool1 = new StubTool("tool_one", "Desc One", List.of(), "r1");
    var tool2 = new StubTool("tool_two", "Desc Two", List.of(), "r2");
    var registrar = new ToolRegistrar(List.of(tool1, tool2), objectMapper, errorStore);

    var catalog = registrar.catalog();

    assertThat(catalog).containsEntry("tool_one", "Desc One");
    assertThat(catalog).containsEntry("tool_two", "Desc Two");
    assertThat(catalog).hasSize(2);
  }

  @Test
  void getCallbacks_withNames_returnsOnlyMatchingTools() {
    var tool1 = new StubTool("tool_one", "Desc One", List.of(), "r1");
    var tool2 = new StubTool("tool_two", "Desc Two", List.of(), "r2");
    var tool3 = new StubTool("tool_three", "Desc Three", List.of(), "r3");
    var registrar = new ToolRegistrar(List.of(tool1, tool2, tool3), objectMapper, errorStore);

    var callbacks = registrar.getCallbacks(List.of("tool_one", "tool_three"));

    assertThat(callbacks).hasSize(2);
    assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tool_one");
    assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("tool_three");
  }

  @Test
  void getCallbacks_withEmptyNames_returnsEmpty() {
    var tool1 = new StubTool("tool_one", "Desc One", List.of(), "r1");
    var registrar = new ToolRegistrar(List.of(tool1), objectMapper, errorStore);

    var callbacks = registrar.getCallbacks(List.of());

    assertThat(callbacks).isEmpty();
  }

  @Test
  void getCallbacks_withUnknownNames_returnsEmpty() {
    var tool1 = new StubTool("tool_one", "Desc One", List.of(), "r1");
    var registrar = new ToolRegistrar(List.of(tool1), objectMapper, errorStore);

    var callbacks = registrar.getCallbacks(List.of("nonexistent"));

    assertThat(callbacks).isEmpty();
  }

  private record StubTool(
      String name, String description, List<ToolProperty> properties, String result)
      implements DaneelToolInterface {
    @Override
    public String execute(Map<String, Object> params) {
      return result;
    }
  }
}
