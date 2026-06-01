package org.daneel.tool;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
public class LocalTimezoneTool implements DaneelToolInterface {

    @Override
    public String name() {
        return "local_timezone";
    }

    @Override
    public String description() {
        return "Returns the local system timezone identifier.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of();
    }

    @Override
    public String execute(Map<String, Object> params) {
        return ZoneId.systemDefault().getId();
    }
}
