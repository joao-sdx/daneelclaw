package org.daneel.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.error.ErrorStore;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistrar {

  private final List<DaneelToolInterface> tools;
  private final ObjectMapper objectMapper;
  private final ErrorStore errorStore;

  public ToolCallback[] getCallbacks() {
    return tools.stream().map(this::toCallback).toArray(ToolCallback[]::new);
  }

  private ToolCallback toCallback(DaneelToolInterface tool) {
    var schema = buildSchema(tool.properties());
    var definition =
        ToolDefinition.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(schema)
            .build();
    return new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return definition;
      }

      @Override
      @SneakyThrows
      public String call(String toolInput) {
        Map<String, Object> params = objectMapper.readValue(toolInput, new TypeReference<>() {});
        log.info("tool_call name={} params={}", tool.name(), params);
        try {
          return tool.execute(params);
        } catch (Exception ex) {
          log.error("tool_error name={} message={}", tool.name(), ex.getMessage(), ex);
          errorStore.record(tool.name(), ex.getMessage());
          return "Error: " + ex.getMessage();
        }
      }
    };
  }

  private String buildSchema(List<ToolProperty> properties) {
    var schemaObj = new LinkedHashMap<String, Object>();
    schemaObj.put("type", "object");
    var propsMap = new LinkedHashMap<String, Object>();
    var requiredList = new ArrayList<String>();
    for (var p : properties) {
      var propDef = new LinkedHashMap<String, Object>();
      propDef.put("type", p.type());
      propDef.put("description", p.description());
      if ("array".equals(p.type())) {
        var elementType = p.itemType() != null ? p.itemType() : "string";
        propDef.put("items", Map.of("type", elementType));
      }
      propsMap.put(p.name(), propDef);
      if (p.required()) {
        requiredList.add(p.name());
      }
    }
    schemaObj.put("properties", propsMap);
    if (!requiredList.isEmpty()) {
      schemaObj.put("required", requiredList);
    }
    return objectMapper.valueToTree(schemaObj).toString();
  }
}
