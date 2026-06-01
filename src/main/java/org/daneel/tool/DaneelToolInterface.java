package org.daneel.tool;

import java.util.List;
import java.util.Map;

public interface DaneelToolInterface {
    String name();
    String description();
    List<ToolProperty> properties();
    String execute(Map<String, Object> params);
}
