package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadListTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadListToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadListTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadListTool(client, new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsJsonArray() throws Exception {
    when(client.listRecords("Leads", "", 20, 0))
        .thenReturn(List.of(Map.of("id", "1", "name", "Doe")));

    var result = tool.execute(Map.of());

    assertThat(result).contains("\"id\"").contains("\"1\"");
  }

  @Test
  void execute_withLimitAndOffset_forwardsToClient() throws Exception {
    when(client.listRecords("Leads", "", 5, 10)).thenReturn(List.of());

    tool.execute(Map.of("limit", 5, "offset", 10));

    verify(client).listRecords("Leads", "", 5, 10);
  }

  @Test
  void execute_withConditions_forwardsToClient() throws Exception {
    var conditions =
        "{\"conditions\":[{\"fieldname\":\"lastname\",\"value\":\"Smith\",\"operator\":\"e\"}]}";
    when(client.listRecords("Leads", conditions, 20, 0)).thenReturn(List.of());

    tool.execute(Map.of("conditions", conditions));

    verify(client).listRecords("Leads", conditions, 20, 0);
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.listRecords(any(), any(), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("connection error"));

    assertThat(tool.execute(Map.of())).startsWith("Error:");
  }
}
