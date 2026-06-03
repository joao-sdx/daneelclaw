package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadUpdateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadUpdateToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadUpdateTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadUpdateTool(client, new YetiForceFieldsConfig(), new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsUpdatedId() throws Exception {
    when(client.updateRecord(eq("Leads"), eq("42"), any())).thenReturn(Map.of("id", "42"));

    var result = tool.execute(Map.of("id", "42", "lastname", "Smith"));

    assertThat(result).contains("42");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).isEqualTo("Error: id is required");
  }

  @Test
  void execute_partialUpdate_onlyPassesProvidedFields() throws Exception {
    when(client.updateRecord(eq("Leads"), eq("42"), any())).thenReturn(Map.of("id", "42"));

    tool.execute(Map.of("id", "42", "email", "new@email.com"));

    @SuppressWarnings("unchecked")
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(client).updateRecord(eq("Leads"), eq("42"), captor.capture());
    assertThat(captor.getValue()).containsOnlyKeys("email");
  }
}
