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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskListToolTest {

    @Mock
    private TaskStore taskStore;

    private TaskListTool tool;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tool = new TaskListTool(taskStore, objectMapper);
    }

    @Test
    void name_isTaskList() {
        assertThat(tool.name()).isEqualTo("task_list");
    }

    @Test
    void description_isNotBlank() {
        assertThat(tool.description()).isNotBlank();
    }

    @Test
    void properties_isEmpty() {
        assertThat(tool.properties()).isEmpty();
    }

    @Test
    void execute_returnsJsonArray() {
        var task = new PlannedTask("id1", "My Task", "hello.md",
                Instant.parse("2026-06-02T10:00:00Z"), null, true);
        when(taskStore.findAll()).thenReturn(List.of(task));
        var result = tool.execute(Map.of());
        assertThat(result).contains("id1").contains("My Task").contains("hello.md");
    }

    @Test
    void execute_whenEmpty_returnsEmptyJsonArray() {
        when(taskStore.findAll()).thenReturn(List.of());
        assertThat(tool.execute(Map.of())).isEqualTo("[]");
    }
}
