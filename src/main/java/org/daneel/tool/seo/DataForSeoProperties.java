package org.daneel.tool.seo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "daneel.tools.dataforseo.api")
@Validated
@Getter
@Setter
public class DataForSeoProperties {

  @NotBlank private String user;
  @NotBlank private String key;
}
