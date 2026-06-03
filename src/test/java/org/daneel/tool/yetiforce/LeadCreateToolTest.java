package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadCreateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadCreateToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadCreateTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadCreateTool(client, new YetiForceFieldsConfig(), new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsCreatedRecord() throws Exception {
    when(client.createRecord(eq("Leads"), any()))
        .thenReturn(Map.of("id", "101", "name", "John Doe"));

    var result = tool.execute(Map.of("lastname", "Doe", "firstname", "John"));

    assertThat(result).contains("101");
  }

  @Test
  void execute_missingRequiredField_returnsError() {
    var params = new HashMap<String, Object>();
    assertThat(tool.execute(params)).startsWith("Error: lastname");
  }

  @Test
  void execute_blankOptionalField_notPassedToClient() throws Exception {
    when(client.createRecord(eq("Leads"), any())).thenReturn(Map.of("id", "1", "name", "Doe"));

    tool.execute(Map.of("lastname", "Doe", "firstname", "  "));

    @SuppressWarnings("unchecked")
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(client).createRecord(eq("Leads"), captor.capture());
    assertThat(captor.getValue()).containsKey("lastname");
    assertThat(captor.getValue()).doesNotContainKey("firstname");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.createRecord(any(), any())).thenThrow(new RuntimeException("server error"));

    assertThat(tool.execute(Map.of("lastname", "Doe"))).startsWith("Error:");
  }
}
