package org.daneel.tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TimeProviderTool implements DaneelToolInterface {

  @Override
  public String name() {
    return "time_provider";
  }

  @Override
  public String description() {
    return "Returns the current date and time for a given timezone.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "timezone",
            "IANA timezone identifier (e.g. America/New_York, Europe/London)",
            "string",
            true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var raw = params.get("timezone");
    if (raw == null || raw.toString().isBlank()) {
      return "Error: timezone parameter is required.";
    }
    return ZonedDateTime.now(ZoneId.of(raw.toString()))
        .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
  }
}
