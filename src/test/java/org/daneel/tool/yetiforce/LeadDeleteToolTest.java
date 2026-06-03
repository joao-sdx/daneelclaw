package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadDeleteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadDeleteToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadDeleteTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadDeleteTool(client);
  }

  @Test
  void execute_happyPath_deletesAndReturnsSuccess() throws Exception {
    var result = tool.execute(Map.of("id", "42"));

    verify(client).deleteRecord("Leads", "42");
    assertThat(result).contains("true");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).isEqualTo("Error: id is required");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    doThrow(new RuntimeException("record not found")).when(client).deleteRecord(any(), any());

    assertThat(tool.execute(Map.of("id", "99"))).startsWith("Error:");
  }
}
