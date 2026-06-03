package org.daneel.tool.yetiforce.accounts;

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
public class AccountDeleteTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;

  @Override
  public String name() {
    return "yetiforce_account_delete";
  }

  @Override
  public String description() {
    return "Deletes an Account record from YetiForce CRM (moves it to trash). "
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
      client.deleteRecord("Accounts", id.toString());
      log.info("yetiforce_account_delete id={}", id);
      return "{\"success\": true}";
    } catch (Exception e) {
      log.warn("yetiforce_account_delete_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
