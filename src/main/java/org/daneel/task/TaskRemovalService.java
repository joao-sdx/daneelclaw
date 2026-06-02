package org.daneel.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRemovalService {

  private final TaskStore taskStore;
  private final PromptCatalog promptCatalog;

  public void remove(String id) {
    var task = taskStore.findById(id).orElse(null);
    taskStore.delete(id);
    if (task == null) {
      return;
    }
    var promptFile = task.promptFile();
    if (!promptCatalog.isAdhoc(promptFile)) {
      return;
    }
    var stillReferenced =
        taskStore.findAll().stream().anyMatch(t -> promptFile.equals(t.promptFile()));
    if (!stillReferenced) {
      promptCatalog.delete(promptFile);
      log.info("adhoc_prompt_deleted file={} taskId={}", promptFile, id);
    }
  }
}
