package org.daneel.tool.yetiforce;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class YetiForceFieldsConfig {

  private final Map<String, List<YetiForceField>> fields;

  @SneakyThrows
  public YetiForceFieldsConfig() {
    var mapper = new ObjectMapper(new YAMLFactory());
    try (var stream = getClass().getClassLoader().getResourceAsStream("yetiforce-fields.yml")) {
      if (stream == null) {
        throw new IllegalStateException("classpath resource yetiforce-fields.yml not found");
      }
      fields = mapper.readValue(stream, new TypeReference<>() {});
    }
    log.info("yetiforce_fields_loaded modules={}", fields.keySet());
  }

  public List<YetiForceField> getFields(String module) {
    return fields.getOrDefault(module, List.of()).stream().filter(YetiForceField::daneel).toList();
  }
}
