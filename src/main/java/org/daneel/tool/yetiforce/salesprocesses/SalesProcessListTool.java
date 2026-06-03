package org.daneel.tool.yetiforce.salesprocesses;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesProcessListTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_sales_process_list";
  }

  @Override
  public String description() {
    return "Lists SalesProcesses records from YetiForce CRM. "
        + "Supports optional JSON conditions filter, limit, and offset for pagination. "
        + "Returns a JSON array of records.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "conditions",
            "Optional JSON filter. "
                + "Example: {\"conditions\":[{\"fieldname\":\"subject\",\"value\":\"Deal\",\"operator\":\"e\"}]}",
            "string",
            false),
        new ToolProperty("limit", "Max records to return (default 20)", "integer", false),
        new ToolProperty("offset", "Pagination offset (default 0)", "integer", false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var conditions = Objects.toString(params.get("conditions"), "");
    var limit = parseIntOrDefault(params.get("limit"), 20);
    var offset = parseIntOrDefault(params.get("offset"), 0);
    try {
      var records = client.listRecords("SalesProcesses", conditions, limit, offset);
      log.info("yetiforce_sales_process_list count={}", records.size());
      return objectMapper.writeValueAsString(records);
    } catch (Exception e) {
      log.warn("yetiforce_sales_process_list_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }

  private int parseIntOrDefault(Object raw, int def) {
    if (raw == null) {
      return def;
    }
    try {
      return Integer.parseInt(raw.toString());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
