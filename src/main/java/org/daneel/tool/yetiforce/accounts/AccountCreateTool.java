package org.daneel.tool.yetiforce.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_account_create";
  }

  @Override
  public String description() {
    return "Creates a new Account record in YetiForce CRM. Returns the created record's id and name.";
  }

  @Override
  public List<ToolProperty> properties() {
    return fieldsConfig.getFields("Accounts").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), f.required()))
        .toList();
  }

  @Override
  public String execute(Map<String, Object> params) {
    for (var field : fieldsConfig.getFields("Accounts")) {
      if (field.required()) {
        var val = params.get(field.name());
        if (val == null || val.toString().isBlank()) {
          return "Error: " + field.name() + " is required";
        }
      }
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Accounts")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.createRecord("Accounts", data);
      log.info("yetiforce_account_create id={}", result.get("id"));
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_account_create_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
