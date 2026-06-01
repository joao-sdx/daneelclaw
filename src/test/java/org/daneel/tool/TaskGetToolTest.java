package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.daneel.task.PlannedTask;
import org.daneel.task.TaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskGetToolTest {

    @Mock
    private TaskStore taskStore;

    private TaskGetTool tool;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tool = new TaskGetTool(taskStore, objectMapper);
    }

    @Test
    void execute_returnsTaskJson() {
        var task = new PlannedTask("abc", "Test", "hello.md",
                Instant.parse("2026-06-02T10:00:00Z"), null, true);
        when(taskStore.findById("abc")).thenReturn(Optional.of(task));
        var result = tool.execute(Map.of("id", "abc"));
        assertThat(result).contains("abc").contains("Test");
    }

    @Test
    void execute_whenNotFound_returnsError() {
        when(taskStore.findById("x")).thenReturn(Optional.empty());
        assertThat(tool.execute(Map.of("id", "x"))).startsWith("Error:");
    }

    @Test
    void execute_missingId_returnsError() {
        var params = new HashMap<String, Object>();
        params.put("id", null);
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_blankId_returnsError() {
        assertThat(tool.execute(Map.of("id", "  "))).startsWith("Error:");
    }
}
