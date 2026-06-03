package org.daneel.tool.yetiforce.salesprocesses;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SalesProcessGetTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_sales_process_get";
  }

  @Override
  public String description() {
    return "Gets a single SalesProcesses record from YetiForce CRM by its ID. Returns full record as JSON.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      var record = client.getRecord("SalesProcesses", id.toString());
      log.info("yetiforce_sales_process_get id={}", id);
      return objectMapper.writeValueAsString(record);
    } catch (Exception e) {
      log.warn("yetiforce_sales_process_get_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
