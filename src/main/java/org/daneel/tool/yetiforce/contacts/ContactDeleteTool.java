package org.daneel.tool.yetiforce.contacts;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactDeleteTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;

  @Override
  public String name() {
    return "yetiforce_contact_delete";
  }

  @Override
  public String description() {
    return "Deletes a Contact record from YetiForce CRM (moves it to trash). "
        + "Returns {\"success\": true} on success.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID to delete", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      client.deleteRecord("Contacts", id.toString());
      log.info("yetiforce_contact_delete id={}", id);
      return "{\"success\": true}";
    } catch (Exception e) {
      log.warn("yetiforce_contact_delete_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
