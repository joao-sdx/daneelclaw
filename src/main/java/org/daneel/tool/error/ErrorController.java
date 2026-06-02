package org.daneel.tool.error;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ErrorController {

  private final ErrorStore errorStore;

  @GetMapping("/errors")
  public List<ToolError> errors() {
    var drained = errorStore.drain();
    if (!drained.isEmpty()) {
      log.info("errors_drained count={}", drained.size());
    }
    return drained;
  }
}
