package org.daneel.tool.error;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ErrorController.class)
class ErrorControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ErrorStore errorStore;

  @Test
  void getErrors_returnsEmptyList_whenNoErrors() throws Exception {
    when(errorStore.drain()).thenReturn(List.of());

    mockMvc.perform(get("/errors")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void getErrors_returnsErrors_fromStore() throws Exception {
    var error = new ToolError("id-1", "my_tool", "it broke", Instant.parse("2026-06-02T12:00:00Z"));
    when(errorStore.drain()).thenReturn(List.of(error));

    mockMvc
        .perform(get("/errors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("id-1"))
        .andExpect(jsonPath("$[0].toolName").value("my_tool"))
        .andExpect(jsonPath("$[0].message").value("it broke"));
  }

  @Test
  void getErrors_drainIsCalledOnEachRequest() throws Exception {
    when(errorStore.drain()).thenReturn(List.of());

    mockMvc.perform(get("/errors"));
    mockMvc.perform(get("/errors"));

    verify(errorStore, org.mockito.Mockito.times(2)).drain();
  }
}
