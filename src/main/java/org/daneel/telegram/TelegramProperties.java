package org.daneel.telegram;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "daneel.telegram")
@Getter
@Setter
public class TelegramProperties {

  private boolean enabled;
  private String botToken = "";
  private List<Long> allowedChatIds = new ArrayList<>();
  private long pollDelayMs = 1000;
}
