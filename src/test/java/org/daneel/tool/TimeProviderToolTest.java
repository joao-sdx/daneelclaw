package org.daneel.tool;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeProviderToolTest {

    private final TimeProviderTool tool = new TimeProviderTool();

    @Test
    void execute_returnsIso8601DateTimeForUtc() {
        var result = tool.execute(Map.of("timezone", "UTC"));
        var parsed = ZonedDateTime.parse(result, DateTimeFormatter.ISO_ZONED_DATE_TIME);
        assertThat(parsed.getZone().getId()).isEqualTo("UTC");
    }

    @Test
    void execute_returnsDateTimeForTokyoTimezone() {
        var result = tool.execute(Map.of("timezone", "Asia/Tokyo"));
        var parsed = ZonedDateTime.parse(result, DateTimeFormatter.ISO_ZONED_DATE_TIME);
        assertThat(parsed.getZone().getId()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void execute_throwsForInvalidTimezone() {
        assertThatThrownBy(() -> tool.execute(Map.of("timezone", "Not/A/Zone")))
                .isInstanceOf(Exception.class);
    }

    @Test
    void execute_returnsErrorForMissingTimezone() {
        assertThat(tool.execute(Map.of())).startsWith("Error:");
    }

    @Test
    void name_isTimeProvider() {
        assertThat(tool.name()).isEqualTo("time_provider");
    }

    @Test
    void properties_hasOneRequiredTimezoneParam() {
        var props = tool.properties();
        assertThat(props).hasSize(1);
        assertThat(props.getFirst().name()).isEqualTo("timezone");
        assertThat(props.getFirst().type()).isEqualTo("string");
        assertThat(props.getFirst().required()).isTrue();
    }
}
