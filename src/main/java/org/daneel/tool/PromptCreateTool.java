package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.PromptDocument;
import org.daneel.task.PromptSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PromptCreateTool implements DaneelToolInterface {

    private final String tasksDir;
    private final ObjectMapper objectMapper;

    public PromptCreateTool(
            @Value("${daneel.scheduler.tasks-dir:./tasks}") String tasksDir,
            ObjectMapper objectMapper) {
        this.tasksDir = tasksDir;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "prompt_create";
    }

    @Override
    public String description() {
        return "Creates a new prompt file that can later be scheduled as a task. "
                + "The filename is auto-generated and returned. "
                + "Cannot modify or delete existing prompts. "
                + "The body may use placeholders {trigger_time_gmt}, {current_time_gmt}, "
                + "{trigger_time_local}, {current_time_local}.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(
                new ToolProperty("summary",
                        "One-line description of what the prompt does (shown by prompt_list)",
                        "string", true),
                new ToolProperty("body",
                        "The prompt text sent to the LLM when the task fires. "
                                + "May use time placeholders.",
                        "string", true)
        );
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        var summaryRaw = params.get("summary");
        if (summaryRaw == null || summaryRaw.toString().isBlank()) {
            return "Error: summary parameter is required.";
        }
        var bodyRaw = params.get("body");
        if (bodyRaw == null || bodyRaw.toString().isBlank()) {
            return "Error: body parameter is required.";
        }
        var summary = summaryRaw.toString();
        var body = bodyRaw.toString();

        var fileName = "p" + Instant.now().toEpochMilli() + ".md";
        var dir = Path.of(tasksDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), PromptDocument.render(summary, body));

        log.info("prompt_created_by_llm file={}", fileName);
        return objectMapper.writeValueAsString(new PromptSummary(fileName, summary));
    }
}
