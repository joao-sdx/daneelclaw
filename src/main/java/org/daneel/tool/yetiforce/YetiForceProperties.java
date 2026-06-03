package org.daneel.tool.yetiforce;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "daneel.tools.yetiforce")
@Validated
@Getter
@Setter
public class YetiForceProperties {

  @NotBlank private String url;
  @NotBlank private String apiKey;
  @NotBlank private String appName;
  @NotBlank private String appPass;
  @NotBlank private String user;
  @NotBlank private String password;
}
