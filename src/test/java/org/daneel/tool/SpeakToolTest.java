package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.daneel.tool.speak.SpeakTool;
import org.junit.jupiter.api.Test;

class SpeakToolTest {

  // Uses "echo {text}" as command — cross-platform, exits 0, doesn't need a voice
  private final SpeakTool tool = new SpeakTool("echo {text}");

  @Test
  void execute_runsCommandAndReturnsDone() {
    assertThat(tool.execute(Map.of("text", "hello"))).isEqualTo("Done.");
  }

  @Test
  void execute_returnsErrorForMissingText() {
    assertThat(tool.execute(Map.of())).startsWith("Error:");
  }

  @Test
  void execute_returnsErrorForBlankText() {
    assertThat(tool.execute(Map.of("text", "   "))).startsWith("Error:");
  }

  @Test
  void name_isSpeak() {
    assertThat(tool.name()).isEqualTo("speak");
  }

  @Test
  void properties_hasOneRequiredTextParam() {
    var props = tool.properties();
    assertThat(props).hasSize(1);
    assertThat(props.getFirst().name()).isEqualTo("text");
    assertThat(props.getFirst().required()).isTrue();
  }
}
