package org.daneel.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalTimezoneToolTest {

  private final LocalTimezoneTool tool = new LocalTimezoneTool();

  @Test
  void execute_returnsSystemTimezone() {
    assertThat(tool.execute(Map.of())).isEqualTo(ZoneId.systemDefault().getId());
  }

  @Test
  void name_isLocalTimezone() {
    assertThat(tool.name()).isEqualTo("local_timezone");
  }

  @Test
  void description_isNotBlank() {
    assertThat(tool.description()).isNotBlank();
  }

  @Test
  void properties_isEmpty() {
    assertThat(tool.properties()).isEmpty();
  }
}
