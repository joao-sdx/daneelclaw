package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.daneel.tool.error.ErrorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ToolRegistrarErrorCaptureTest {

  @Mock private ErrorStore errorStore;

  private ToolRegistrar registrar;

  private DaneelToolInterface throwingTool;

  @BeforeEach
  void setUp() {
    throwingTool =
        new DaneelToolInterface() {
          @Override
          public String name() {
            return "boom_tool";
          }

          @Override
          public String description() {
            return "A tool that always throws";
          }

          @Override
          public List<ToolProperty> properties() {
            return List.of();
          }

          @Override
          public String execute(Map<String, Object> params) {
            throw new RuntimeException("kaboom");
          }
        };

    registrar = new ToolRegistrar(List.of(throwingTool), new ObjectMapper(), errorStore);
  }

  @Test
  void call_whenToolThrows_returnsErrorString() {
    var callback = registrar.getCallbacks()[0];

    var result = callback.call("{}");

    assertThat(result).startsWith("Error:").contains("kaboom");
  }

  @Test
  void call_whenToolThrows_recordsToErrorStore() {
    var callback = registrar.getCallbacks()[0];

    callback.call("{}");

    verify(errorStore).record(eq("boom_tool"), eq("kaboom"));
  }

  @Test
  void call_whenToolSucceeds_doesNotRecordError() {
    DaneelToolInterface okTool =
        new DaneelToolInterface() {
          @Override
          public String name() {
            return "ok_tool";
          }

          @Override
          public String description() {
            return "A tool that succeeds";
          }

          @Override
          public List<ToolProperty> properties() {
            return List.of();
          }

          @Override
          public String execute(Map<String, Object> params) {
            return "success";
          }
        };

    var okRegistrar = new ToolRegistrar(List.of(okTool), new ObjectMapper(), errorStore);
    var callback = okRegistrar.getCallbacks()[0];

    var result = callback.call("{}");

    assertThat(result).isEqualTo("success");
    org.mockito.Mockito.verifyNoInteractions(errorStore);
  }
}
