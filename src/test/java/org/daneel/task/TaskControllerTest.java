package org.daneel.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean TaskStore taskStore;
  @MockitoBean TaskRemovalService taskRemovalService;

  @Test
  void list_returnsEmptyList() throws Exception {
    when(taskStore.findAll()).thenReturn(List.of());
    mockMvc.perform(get("/tasks")).andExpect(status().isOk()).andExpect(content().json("[]"));
  }

  @Test
  void list_returnsTasks() throws Exception {
    when(taskStore.findAll()).thenReturn(List.of(task("t1")));
    mockMvc
        .perform(get("/tasks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("t1"));
  }

  @Test
  void get_returnsTask() throws Exception {
    when(taskStore.findById("t1")).thenReturn(Optional.of(task("t1")));
    mockMvc
        .perform(get("/tasks/t1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("t1"));
  }

  @Test
  void get_returns404WhenNotFound() throws Exception {
    when(taskStore.findById("missing")).thenReturn(Optional.empty());
    mockMvc.perform(get("/tasks/missing")).andExpect(status().isNotFound());
  }

  @Test
  void create_savesTaskAndReturns201() throws Exception {
    mockMvc
        .perform(
            post("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"name":"My task","promptFile":"my.md",
                                 "nextRunAt":"2026-06-01T14:00:00Z",
                                 "recurringIntervalMinutes":60,"enabled":true}
                                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("My task"))
        .andExpect(jsonPath("$.id").isNotEmpty());
    verify(taskStore).save(any(PlannedTask.class));
  }

  @Test
  void update_updatesExistingTask() throws Exception {
    when(taskStore.findById("t1")).thenReturn(Optional.of(task("t1")));
    mockMvc
        .perform(
            put("/tasks/t1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"name":"Updated","promptFile":"u.md",
                                 "nextRunAt":"2026-06-01T15:00:00Z",
                                 "recurringIntervalMinutes":null,"enabled":false}
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated"));
    verify(taskStore).save(any(PlannedTask.class));
  }

  @Test
  void update_returns404WhenNotFound() throws Exception {
    when(taskStore.findById("missing")).thenReturn(Optional.empty());
    mockMvc
        .perform(
            put("/tasks/missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {"name":"X","promptFile":"x.md",
                                 "nextRunAt":"2026-06-01T14:00:00Z",
                                 "recurringIntervalMinutes":null,"enabled":true}
                                """))
        .andExpect(status().isNotFound());
  }

  @Test
  void delete_returns204() throws Exception {
    when(taskStore.findById("t1")).thenReturn(Optional.of(task("t1")));
    mockMvc.perform(delete("/tasks/t1")).andExpect(status().isNoContent());
    verify(taskRemovalService).remove("t1");
  }

  @Test
  void delete_returns404WhenNotFound() throws Exception {
    when(taskStore.findById("missing")).thenReturn(Optional.empty());
    mockMvc.perform(delete("/tasks/missing")).andExpect(status().isNotFound());
  }

  private PlannedTask task(String id) {
    return new PlannedTask(
        id, "Task " + id, id + ".md", Instant.parse("2026-06-01T14:00:00Z"), 60, true);
  }
}
