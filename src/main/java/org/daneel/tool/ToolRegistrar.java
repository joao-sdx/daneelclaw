package org.daneel.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistrar {

    private final List<DaneelToolInterface> tools;
    private final ObjectMapper objectMapper;

    public ToolCallback[] getCallbacks() {
        return tools.stream()
                .map(this::toCallback)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback toCallback(DaneelToolInterface tool) {
        var schema = buildSchema(tool.properties());
        var definition = ToolDefinition.builder()
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
                Map<String, Object> params = objectMapper.readValue(
                        toolInput, new TypeReference<>() {});
                log.info("tool_call name={} params={}", tool.name(), params);
                return tool.execute(params);
            }
        };
    }

    private String buildSchema(List<ToolProperty> properties) {
        if (properties.isEmpty()) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        var props = properties.stream()
                .map(p -> "\"" + p.name() + "\":{\"type\":\"" + p.type()
                        + "\",\"description\":\"" + p.description() + "\"}")
                .collect(Collectors.joining(","));
        var required = properties.stream()
                .filter(ToolProperty::required)
                .map(p -> "\"" + p.name() + "\"")
                .collect(Collectors.joining(","));
        var schema = new StringBuilder("{\"type\":\"object\",\"properties\":{")
                .append(props)
                .append("}");
        if (!required.isEmpty()) {
            schema.append(",\"required\":[").append(required).append("]");
        }
        return schema.append("}").toString();
    }
}
