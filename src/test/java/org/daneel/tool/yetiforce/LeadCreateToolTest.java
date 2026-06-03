package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
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

  private final YetiForceFieldsConfig fieldsConfig = new YetiForceFieldsConfig();

  private LeadCreateTool tool;
  private List<String> requiredFields;
  private String optionalField;

  @BeforeEach
  void setUp() {
    tool = new LeadCreateTool(client, fieldsConfig, new ObjectMapper());
    var fields = fieldsConfig.getFields("Leads");
    requiredFields =
        fields.stream().filter(YetiForceField::required).map(YetiForceField::name).toList();
    optionalField =
        fields.stream()
            .filter(f -> !f.required())
            .map(YetiForceField::name)
            .findFirst()
            .orElseThrow();
  }

  private Map<String, Object> allRequired() {
    var params = new HashMap<String, Object>();
    requiredFields.forEach(name -> params.put(name, "x"));
    return params;
  }

  @Test
  void execute_happyPath_returnsCreatedRecord() throws Exception {
    when(client.createRecord(eq("Leads"), any())).thenReturn(Map.of("id", "101", "name", "Acme"));

    var result = tool.execute(allRequired());

    assertThat(result).contains("101");
  }

  @Test
  void execute_missingRequiredField_returnsError() {
    assertThat(tool.execute(new HashMap<>())).startsWith("Error: " + requiredFields.getFirst());
  }

  @Test
  void execute_blankOptionalField_notPassedToClient() throws Exception {
    when(client.createRecord(eq("Leads"), any())).thenReturn(Map.of("id", "1", "name", "Acme"));

    var params = allRequired();
    params.put(optionalField, "  ");
    tool.execute(params);

    @SuppressWarnings("unchecked")
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(client).createRecord(eq("Leads"), captor.capture());
    assertThat(captor.getValue()).containsKey(requiredFields.getFirst());
    assertThat(captor.getValue()).doesNotContainKey(optionalField);
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.createRecord(any(), any())).thenThrow(new RuntimeException("server error"));

    assertThat(tool.execute(allRequired())).startsWith("Error:");
  }
}
