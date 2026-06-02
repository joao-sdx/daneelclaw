package org.daneel.tool.speak;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SpeakTool implements DaneelToolInterface {

  private final String commandTemplate;

  public SpeakTool(
      @Value("${daneel.tools.speak.command:say -v Thomas {text}}") String commandTemplate) {
    this.commandTemplate = commandTemplate;
  }

  @Override
  public String name() {
    return "speak";
  }

  @Override
  public String description() {
    return "Speaks text aloud using the system's text-to-speech command.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("text", "The text to speak aloud", "string", true));
  }

  @Override
  @SneakyThrows
  public String execute(Map<String, Object> params) {
    var raw = params.get("text");
    if (raw == null || raw.toString().isBlank()) {
      return "Error: text parameter is required.";
    }
    var text = raw.toString();
    var args =
        Arrays.stream(commandTemplate.split(" "))
            .map(token -> token.replace("{text}", text))
            .toArray(String[]::new);
    var exitCode = new ProcessBuilder(args).inheritIO().start().waitFor();
    return exitCode == 0 ? "Done." : "Error: command exited with code " + exitCode + ".";
  }
}
