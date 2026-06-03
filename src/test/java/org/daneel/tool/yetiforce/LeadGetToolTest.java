package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadGetTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadGetToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadGetTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadGetTool(client, new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsRecord() throws Exception {
    when(client.getRecord("Leads", "42")).thenReturn(Map.of("id", "42", "lastname", "Doe"));

    var result = tool.execute(Map.of("id", "42"));

    assertThat(result).contains("\"42\"");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).isEqualTo("Error: id is required");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.getRecord(any(), any())).thenThrow(new RuntimeException("not found"));

    assertThat(tool.execute(Map.of("id", "99"))).startsWith("Error:");
  }
}
