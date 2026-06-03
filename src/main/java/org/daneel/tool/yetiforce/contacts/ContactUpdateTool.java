package org.daneel.tool.yetiforce.contacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
public class ContactUpdateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_contact_update";
  }

  @Override
  public String description() {
    return "Updates an existing Contact record in YetiForce CRM. "
        + "Pass only the fields you want to change alongside the required id.";
  }

  @Override
  public List<ToolProperty> properties() {
    var props = new ArrayList<ToolProperty>();
    props.add(new ToolProperty("id", "Record ID to update", "string", true));
    fieldsConfig.getFields("Contacts").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), false))
        .forEach(props::add);
    return props;
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Contacts")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.updateRecord("Contacts", id.toString(), data);
      log.info("yetiforce_contact_update id={}", id);
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_contact_update_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
