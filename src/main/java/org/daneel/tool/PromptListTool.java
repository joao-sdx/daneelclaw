package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.PromptCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptListTool implements DaneelToolInterface {

    private final PromptCatalog promptCatalog;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "prompt_list";
    }

    @Override
    public String description() {
        return "Lists all available prompt files with their summaries. "
                + "Use this to discover which promptFile to use when creating or updating a task.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of();
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        return objectMapper.writeValueAsString(promptCatalog.list());
    }
}
