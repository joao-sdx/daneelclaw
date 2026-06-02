# Task Management Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose CRUD task operations and prompt discovery as bot-facing LLM tools, so DaneelClaw can manage its own
scheduled tasks during a conversation.

**Architecture:** Six `DaneelToolInterface` implementations auto-wired via `ToolRegistrar`. Foundation pieces (
`PromptDocument`, `PromptCatalog`) land first; task tools depend on `TaskStore` and `PromptCatalog`. `PromptResolver` is
patched to strip YAML frontmatter before sending prompt body to the LLM.

**Tech Stack:** Spring Boot 3.5, Lombok, Jackson YAML (already in pom.xml), JUnit 5, Mockito, AssertJ.

---

## File Map

**New files — `src/main/java/org/daneel/task/`**

- `PromptDocument.java` — record + static `parse()` for YAML frontmatter extraction
- `PromptSummary.java` — record `(String promptFile, String summary)`
- `PromptCatalog.java` — `@Component` scanning `*.md` files for frontmatter summaries

**Modified — `src/main/java/org/daneel/task/`**

- `PromptResolver.java` — route content through `PromptDocument.parse().body()` before substitution

**New files — `src/main/java/org/daneel/tool/`**

- `TaskListTool.java` — `task_list`: JSON array of all tasks
- `TaskGetTool.java` — `task_get`: task by id
- `TaskCreateTool.java` — `task_create`: validate prompt exists, generate UUID, save
- `TaskUpdateTool.java` — `task_update`: full replace by id
- `TaskDeleteTool.java` — `task_delete`: remove from store (prompt file untouched)
- `PromptListTool.java` — `prompt_list`: filenames + summaries from `PromptCatalog`

**Modified — `tasks/`**

- `hello.md` — prepend YAML frontmatter with `summary:`

**New test files**

- `src/test/java/org/daneel/task/PromptDocumentTest.java`
- `src/test/java/org/daneel/task/PromptCatalogTest.java`
- (extend) `src/test/java/org/daneel/task/PromptResolverTest.java`
- `src/test/java/org/daneel/tool/TaskListToolTest.java`
- `src/test/java/org/daneel/tool/TaskGetToolTest.java`
- `src/test/java/org/daneel/tool/TaskCreateToolTest.java`
- `src/test/java/org/daneel/tool/TaskUpdateToolTest.java`
- `src/test/java/org/daneel/tool/TaskDeleteToolTest.java`
- `src/test/java/org/daneel/tool/PromptListToolTest.java`

---

### Task 1: PromptDocument — frontmatter parser

**Files:**

- Create: `src/main/java/org/daneel/task/PromptDocument.java`
- Create: `src/test/java/org/daneel/task/PromptDocumentTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/task/PromptDocumentTest.java
package org.daneel.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptDocumentTest {

    @Test
    void parse_withFrontmatter_extractsSummaryAndBody() {
        var content = "---\nsummary: Says hello out loud.\n---\nUse the speak tool.";
        var doc = PromptDocument.parse(content);
        assertThat(doc.summary()).isEqualTo("Says hello out loud.");
        assertThat(doc.body()).isEqualTo("Use the speak tool.");
    }

    @Test
    void parse_withoutFrontmatter_returnsEmptySummaryAndFullContent() {
        var content = "Use the speak tool.";
        var doc = PromptDocument.parse(content);
        assertThat(doc.summary()).isEmpty();
        assertThat(doc.body()).isEqualTo("Use the speak tool.");
    }

    @Test
    void parse_withUnclosedFrontmatter_returnsEmptySummaryAndFullContent() {
        var content = "---\nsummary: Oops.\nBody without closing delimiter.";
        var doc = PromptDocument.parse(content);
        assertThat(doc.summary()).isEmpty();
        assertThat(doc.body()).isEqualTo(content);
    }

    @Test
    void parse_withFrontmatterNoSummaryKey_returnsEmptySummary() {
        var content = "---\nother: value\n---\nBody here.";
        var doc = PromptDocument.parse(content);
        assertThat(doc.summary()).isEmpty();
        assertThat(doc.body()).isEqualTo("Body here.");
    }

    @Test
    void parse_frontmatterStripsLeadingWhitespaceFromBody() {
        var content = "---\nsummary: Test.\n---\n\nBody after blank line.";
        var doc = PromptDocument.parse(content);
        assertThat(doc.body()).isEqualTo("Body after blank line.");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd /Users/joao.violante/IdeaProjects/daneelclaw
./mvnw test -Dtest=PromptDocumentTest -q 2>&1 | tail -10
```

Expected: compilation error — `PromptDocument` not found.

- [ ] **Step 3: Implement PromptDocument**

```java
// src/main/java/org/daneel/task/PromptDocument.java
package org.daneel.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;

public record PromptDocument(String summary, String body) {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static PromptDocument parse(String content) {
        if (content == null || !content.startsWith("---\n")) {
            return new PromptDocument("", content != null ? content : "");
        }
        var closing = content.indexOf("\n---", 4);
        if (closing == -1) {
            return new PromptDocument("", content);
        }
        var yamlBlock = content.substring(4, closing);
        var afterClosing = content.substring(closing + 4);
        var body = afterClosing.stripLeading();
        return new PromptDocument(extractSummary(yamlBlock), body);
    }

    @SneakyThrows
    private static String extractSummary(String yaml) {
        var node = YAML_MAPPER.readTree(yaml);
        if (node == null) {
            return "";
        }
        var summaryNode = node.get("summary");
        return summaryNode != null ? summaryNode.asText("") : "";
    }
}
```

- [ ] **Step 4: Run tests to confirm pass**

```bash
./mvnw test -Dtest=PromptDocumentTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/task/PromptDocument.java \
        src/test/java/org/daneel/task/PromptDocumentTest.java
git commit -m "feat: add PromptDocument for YAML frontmatter parsing"
```

---

### Task 2: PromptSummary + PromptCatalog

**Files:**

- Create: `src/main/java/org/daneel/task/PromptSummary.java`
- Create: `src/main/java/org/daneel/task/PromptCatalog.java`
- Create: `src/test/java/org/daneel/task/PromptCatalogTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/task/PromptCatalogTest.java
package org.daneel.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PromptCatalogTest {

    @TempDir
    Path tempDir;

    private PromptCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new PromptCatalog(tempDir.toString());
    }

    @Test
    void list_returnsSummaryForEachMdFile() throws Exception {
        Files.writeString(tempDir.resolve("hello.md"),
                "---\nsummary: Says hello.\n---\nSpeak hello.");
        var result = catalog.list();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().promptFile()).isEqualTo("hello.md");
        assertThat(result.getFirst().summary()).isEqualTo("Says hello.");
    }

    @Test
    void list_withNoFrontmatter_returnsEmptySummary() throws Exception {
        Files.writeString(tempDir.resolve("bare.md"), "Just body, no frontmatter.");
        var result = catalog.list();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().summary()).isEmpty();
    }

    @Test
    void list_whenDirIsEmpty_returnsEmptyList() {
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void list_ignoresNonMdFiles() throws Exception {
        Files.writeString(tempDir.resolve("tasks.yml"), "tasks: []");
        Files.writeString(tempDir.resolve("note.txt"), "ignored");
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void exists_whenFilePresent_returnsTrue() throws Exception {
        Files.writeString(tempDir.resolve("hello.md"), "content");
        assertThat(catalog.exists("hello.md")).isTrue();
    }

    @Test
    void exists_whenFileAbsent_returnsFalse() {
        assertThat(catalog.exists("missing.md")).isFalse();
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./mvnw test -Dtest=PromptCatalogTest -q 2>&1 | tail -10
```

Expected: compilation error — `PromptCatalog` / `PromptSummary` not found.

- [ ] **Step 3: Implement PromptSummary and PromptCatalog**

```java
// src/main/java/org/daneel/task/PromptSummary.java
package org.daneel.task;

public record PromptSummary(String promptFile, String summary) {}
```

```java
// src/main/java/org/daneel/task/PromptCatalog.java
package org.daneel.task;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class PromptCatalog {

    private final String tasksDir;

    public PromptCatalog(@Value("${daneel.scheduler.tasks-dir:./tasks}") String tasksDir) {
        this.tasksDir = tasksDir;
    }

    @SneakyThrows
    public List<PromptSummary> list() {
        var dir = Path.of(tasksDir);
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .map(this::toSummary)
                    .toList();
        }
    }

    public boolean exists(String promptFile) {
        return Files.exists(Path.of(tasksDir, promptFile));
    }

    @SneakyThrows
    private PromptSummary toSummary(Path path) {
        var content = Files.readString(path);
        var doc = PromptDocument.parse(content);
        return new PromptSummary(path.getFileName().toString(), doc.summary());
    }
}
```

- [ ] **Step 4: Run tests to confirm pass**

```bash
./mvnw test -Dtest=PromptCatalogTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/task/PromptSummary.java \
        src/main/java/org/daneel/task/PromptCatalog.java \
        src/test/java/org/daneel/task/PromptCatalogTest.java
git commit -m "feat: add PromptSummary and PromptCatalog for prompt discovery"
```

---

### Task 3: Strip frontmatter in PromptResolver

**Files:**

- Modify: `src/main/java/org/daneel/task/PromptResolver.java`
- Modify: `src/test/java/org/daneel/task/PromptResolverTest.java`

- [ ] **Step 1: Add the failing test to PromptResolverTest**

Add this test to the existing `PromptResolverTest` class (after the last existing test):

```java
@Test
void resolve_stripsFrontmatterBeforeSubstitution() throws Exception {
    Files.writeString(tempDir.resolve("task-1.md"),
            "---\nsummary: Test task.\n---\nTriggered at {trigger_time_gmt}");
    var result = resolver.resolve(task,
            Instant.parse("2026-06-01T14:00:00Z"),
            Instant.parse("2026-06-01T14:00:03Z"));
    assertThat(result).doesNotContain("---");
    assertThat(result).doesNotContain("summary:");
    assertThat(result).startsWith("Triggered at");
    assertThat(result).contains("2026-06-01T14:00:00Z");
}
```

- [ ] **Step 2: Run to confirm the new test fails**

```bash
./mvnw test -Dtest=PromptResolverTest -q 2>&1 | tail -10
```

Expected: `resolve_stripsFrontmatterBeforeSubstitution` FAIL (frontmatter is currently passed through verbatim).

- [ ] **Step 3: Modify PromptResolver.resolve() to strip frontmatter**

Change only the `resolve` method. Replace the line `var content = Files.readString(file);` and the return block:

```java
// BEFORE
public String resolve(PlannedTask task, Instant triggerTime, Instant currentTime) {
    var file = Path.of(tasksDir, task.promptFile());
    try {
        var content = Files.readString(file);
        return content
                .replace("{trigger_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(triggerTime))
                .replace("{current_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(currentTime))
                .replace("{trigger_time_local}", formatLocal(triggerTime))
                .replace("{current_time_local}", formatLocal(currentTime));
    } catch (IOException e) {
        throw new UncheckedIOException("Cannot read prompt file: " + file, e);
    }
}

// AFTER
public String resolve(PlannedTask task, Instant triggerTime, Instant currentTime) {
    var file = Path.of(tasksDir, task.promptFile());
    try {
        var body = PromptDocument.parse(Files.readString(file)).body();
        return body
                .replace("{trigger_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(triggerTime))
                .replace("{current_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(currentTime))
                .replace("{trigger_time_local}", formatLocal(triggerTime))
                .replace("{current_time_local}", formatLocal(currentTime));
    } catch (IOException e) {
        throw new UncheckedIOException("Cannot read prompt file: " + file, e);
    }
}
```

- [ ] **Step 4: Run all PromptResolver tests**

```bash
./mvnw test -Dtest=PromptResolverTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 6 tests passed (5 original + 1 new).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/task/PromptResolver.java \
        src/test/java/org/daneel/task/PromptResolverTest.java
git commit -m "feat: strip YAML frontmatter in PromptResolver before LLM delivery"
```

---

### Task 4: Add frontmatter to hello.md

**Files:**

- Modify: `tasks/hello.md`

- [ ] **Step 1: Prepend frontmatter**

Replace the full content of `tasks/hello.md` with:

```
---
summary: Says "bonjour" out loud via the speak tool.
---
Use the speak tool to say the word 'bonjour' out loud.
```

- [ ] **Step 2: Verify PromptCatalog picks it up (optional quick check)**

```bash
./mvnw test -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — all existing tests still pass (PromptResolverTest now strips frontmatter, so the body
reaches the resolver cleanly).

- [ ] **Step 3: Commit**

```bash
git add tasks/hello.md
git commit -m "feat: add frontmatter summary to hello.md prompt"
```

---

### Task 5: TaskListTool

**Files:**

- Create: `src/main/java/org/daneel/tool/TaskListTool.java`
- Create: `src/test/java/org/daneel/tool/TaskListToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/tool/TaskListToolTest.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.daneel.task.PlannedTask;
import org.daneel.task.TaskStore;
import org.daneel.tool.task.TaskListTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskListToolTest {

    @Mock
    private TaskStore taskStore;

    private TaskListTool tool;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tool = new TaskListTool(taskStore, objectMapper);
    }

    @Test
    void name_isTaskList() {
        assertThat(tool.name()).isEqualTo("task_list");
    }

    @Test
    void description_isNotBlank() {
        assertThat(tool.description()).isNotBlank();
    }

    @Test
    void properties_isEmpty() {
        assertThat(tool.properties()).isEmpty();
    }

    @Test
    void execute_returnsJsonArray() {
        var task = new PlannedTask("id1", "My Task", "hello.md", Instant.parse("2026-06-02T10:00:00Z"), null, true);
        when(taskStore.findAll()).thenReturn(List.of(task));
        var result = tool.execute(Map.of());
        assertThat(result).contains("id1").contains("My Task").contains("hello.md");
    }

    @Test
    void execute_whenEmpty_returnsEmptyJsonArray() {
        when(taskStore.findAll()).thenReturn(List.of());
        assertThat(tool.execute(Map.of())).isEqualTo("[]");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./mvnw test -Dtest=TaskListToolTest -q 2>&1 | tail -10
```

Expected: compilation error — `TaskListTool` not found.

- [ ] **Step 3: Implement TaskListTool**

```java
// src/main/java/org/daneel/tool/TaskListTool.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskListTool implements DaneelToolInterface {

    private final TaskStore taskStore;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "task_list";
    }

    @Override
    public String description() {
        return "Lists all planned tasks. Returns a JSON array of task objects.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of();
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        return objectMapper.writeValueAsString(taskStore.findAll());
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -Dtest=TaskListToolTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/tool/TaskListTool.java \
        src/test/java/org/daneel/tool/TaskListToolTest.java
git commit -m "feat: add TaskListTool to list all scheduled tasks"
```

---

### Task 6: TaskGetTool

**Files:**

- Create: `src/main/java/org/daneel/tool/TaskGetTool.java`
- Create: `src/test/java/org/daneel/tool/TaskGetToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/tool/TaskGetToolTest.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.daneel.task.PlannedTask;
import org.daneel.task.TaskStore;
import org.daneel.tool.task.TaskGetTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskGetToolTest {

    @Mock
    private TaskStore taskStore;

    private TaskGetTool tool;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tool = new TaskGetTool(taskStore, objectMapper);
    }

    @Test
    void execute_returnsTaskJson() {
        var task = new PlannedTask("abc", "Test", "hello.md", Instant.parse("2026-06-02T10:00:00Z"), null, true);
        when(taskStore.findById("abc")).thenReturn(Optional.of(task));
        var result = tool.execute(Map.of("id", "abc"));
        assertThat(result).contains("abc").contains("Test");
    }

    @Test
    void execute_whenNotFound_returnsError() {
        when(taskStore.findById("x")).thenReturn(Optional.empty());
        assertThat(tool.execute(Map.of("id", "x"))).startsWith("Error:");
    }

    @Test
    void execute_missingId_returnsError() {
        var params = new HashMap<String, Object>();
        params.put("id", null);
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_blankId_returnsError() {
        assertThat(tool.execute(Map.of("id", "  "))).startsWith("Error:");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./mvnw test -Dtest=TaskGetToolTest -q 2>&1 | tail -10
```

Expected: compilation error.

- [ ] **Step 3: Implement TaskGetTool**

```java
// src/main/java/org/daneel/tool/TaskGetTool.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskGetTool implements DaneelToolInterface {

    private final TaskStore taskStore;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "task_get";
    }

    @Override
    public String description() {
        return "Gets a single planned task by its id. Returns the task as JSON, or an error if not found.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(new ToolProperty("id", "The task id", "string", true));
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        var raw = params.get("id");
        if (raw == null || raw.toString().isBlank()) {
            return "Error: id parameter is required.";
        }
        return taskStore.findById(raw.toString())
                .map(task -> {
                    try {
                        return objectMapper.writeValueAsString(task);
                    } catch (Exception e) {
                        return "Error: failed to serialize task.";
                    }
                })
                .orElse("Error: task not found.");
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -Dtest=TaskGetToolTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/tool/TaskGetTool.java \
        src/test/java/org/daneel/tool/TaskGetToolTest.java
git commit -m "feat: add TaskGetTool to retrieve a task by id"
```

---

### Task 7: TaskCreateTool

**Files:**

- Create: `src/main/java/org/daneel/tool/TaskCreateTool.java`
- Create: `src/test/java/org/daneel/tool/TaskCreateToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/tool/TaskCreateToolTest.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.daneel.tool.task.TaskCreateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCreateToolTest {

    @Mock
    private TaskStore taskStore;

    @Mock
    private PromptCatalog promptCatalog;

    private TaskCreateTool tool;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tool = new TaskCreateTool(taskStore, promptCatalog, objectMapper);
    }

    @Test
    void execute_createsAndReturnsTask() {
        when(promptCatalog.exists("hello.md")).thenReturn(true);
        var params = Map.<String, Object>of("name", "My Task", "promptFile", "hello.md", "nextRunAt",
                "2026-06-02T10:00:00Z");
        var result = tool.execute(params);
        var captor = ArgumentCaptor.forClass(PlannedTask.class);
        verify(taskStore).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("My Task");
        assertThat(captor.getValue().enabled()).isTrue();
        assertThat(result).contains("My Task");
    }

    @Test
    void execute_missingName_returnsError() {
        var params = new HashMap<String, Object>();
        params.put("name", null);
        params.put("promptFile", "hello.md");
        params.put("nextRunAt", "2026-06-02T10:00:00Z");
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_unknownPromptFile_returnsError() {
        when(promptCatalog.exists("missing.md")).thenReturn(false);
        var params = Map.<String, Object>of("name", "Task", "promptFile", "missing.md", "nextRunAt",
                "2026-06-02T10:00:00Z");
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_badNextRunAt_returnsError() {
        when(promptCatalog.exists("hello.md")).thenReturn(true);
        var params = Map.<String, Object>of("name", "Task", "promptFile", "hello.md", "nextRunAt", "not-a-date");
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_missingNextRunAt_returnsError() {
        when(promptCatalog.exists("hello.md")).thenReturn(true);
        var params = new HashMap<String, Object>();
        params.put("name", "Task");
        params.put("promptFile", "hello.md");
        params.put("nextRunAt", null);
        assertThat(tool.execute(params)).startsWith("Error:");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./mvnw test -Dtest=TaskCreateToolTest -q 2>&1 | tail -10
```

Expected: compilation error.

- [ ] **Step 3: Implement TaskCreateTool**

```java
// src/main/java/org/daneel/tool/TaskCreateTool.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCreateTool implements DaneelToolInterface {

    private final TaskStore taskStore;
    private final PromptCatalog promptCatalog;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "task_create";
    }

    @Override
    public String description() {
        return "Creates a new planned task using an existing prompt file. "
                + "Use prompt_list to discover available prompt files.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(
                new ToolProperty("name", "Human-readable task name", "string", true),
                new ToolProperty("promptFile",
                        "Filename of an existing prompt file (use prompt_list to see options)",
                        "string", true),
                new ToolProperty("nextRunAt",
                        "When to run the task as an ISO-8601 UTC instant, e.g. 2026-06-02T15:00:00Z",
                        "string", true),
                new ToolProperty("recurringIntervalMinutes",
                        "Recurrence interval in minutes; omit for a one-shot task",
                        "integer", false),
                new ToolProperty("enabled",
                        "Whether the task is active (default true)",
                        "boolean", false)
        );
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        var nameRaw = params.get("name");
        if (nameRaw == null || nameRaw.toString().isBlank()) {
            return "Error: name is required.";
        }
        var promptFileRaw = params.get("promptFile");
        if (promptFileRaw == null || promptFileRaw.toString().isBlank()) {
            return "Error: promptFile is required.";
        }
        if (!promptCatalog.exists(promptFileRaw.toString())) {
            return "Error: prompt file not found: " + promptFileRaw
                    + ". Use prompt_list to see available prompts.";
        }
        var nextRunAtRaw = params.get("nextRunAt");
        if (nextRunAtRaw == null || nextRunAtRaw.toString().isBlank()) {
            return "Error: nextRunAt is required.";
        }
        Instant nextRunAt;
        try {
            nextRunAt = Instant.parse(nextRunAtRaw.toString());
        } catch (DateTimeParseException e) {
            return "Error: nextRunAt must be an ISO-8601 UTC instant, e.g. 2026-06-02T15:00:00Z.";
        }

        var interval = parseInterval(params.get("recurringIntervalMinutes"));
        if (interval instanceof String error) {
            return error;
        }

        var enabled = parseEnabled(params.get("enabled"));
        var task = new PlannedTask(UUID.randomUUID().toString(), nameRaw.toString(),
                promptFileRaw.toString(), nextRunAt, (Integer) interval, enabled);
        taskStore.save(task);
        log.info("task_created_by_llm id={} name={}", task.id(), task.name());
        return objectMapper.writeValueAsString(task);
    }

    private Object parseInterval(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        var s = raw.toString().trim();
        if (s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return "Error: recurringIntervalMinutes must be an integer.";
        }
    }

    private boolean parseEnabled(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw != null) {
            return Boolean.parseBoolean(raw.toString());
        }
        return true;
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -Dtest=TaskCreateToolTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/tool/TaskCreateTool.java \
        src/test/java/org/daneel/tool/TaskCreateToolTest.java
git commit -m "feat: add TaskCreateTool to schedule a task via the LLM"
```

---

### Task 8: TaskUpdateTool

**Files:**

- Create: `src/main/java/org/daneel/tool/TaskUpdateTool.java`
- Create: `src/test/java/org/daneel/tool/TaskUpdateToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/tool/TaskUpdateToolTest.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.daneel.tool.task.TaskUpdateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskUpdateToolTest {

    @Mock
    private TaskStore taskStore;

    @Mock
    private PromptCatalog promptCatalog;

    private TaskUpdateTool tool;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        tool = new TaskUpdateTool(taskStore, promptCatalog, objectMapper);
    }

    private PlannedTask existingTask() {
        return new PlannedTask("id1", "Old Name", "hello.md", Instant.parse("2026-06-01T10:00:00Z"), null, true);
    }

    @Test
    void execute_updatesTask_returnsJson() {
        when(taskStore.findById("id1")).thenReturn(Optional.of(existingTask()));
        when(promptCatalog.exists("hello.md")).thenReturn(true);
        var params = Map.<String, Object>of("id", "id1", "name", "New Name", "promptFile", "hello.md", "nextRunAt",
                "2026-06-03T10:00:00Z", "enabled", true);
        var result = tool.execute(params);
        assertThat(result).contains("New Name").contains("id1");
    }

    @Test
    void execute_taskNotFound_returnsError() {
        when(taskStore.findById("missing")).thenReturn(Optional.empty());
        var params = Map.<String, Object>of("id", "missing", "name", "Name", "promptFile", "hello.md", "nextRunAt",
                "2026-06-03T10:00:00Z", "enabled", true);
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_missingId_returnsError() {
        var params = new HashMap<String, Object>();
        params.put("id", null);
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_unknownPromptFile_returnsError() {
        when(taskStore.findById("id1")).thenReturn(Optional.of(existingTask()));
        when(promptCatalog.exists("bad.md")).thenReturn(false);
        var params = Map.<String, Object>of("id", "id1", "name", "Name", "promptFile", "bad.md", "nextRunAt",
                "2026-06-03T10:00:00Z", "enabled", true);
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void execute_badNextRunAt_returnsError() {
        when(taskStore.findById("id1")).thenReturn(Optional.of(existingTask()));
        when(promptCatalog.exists("hello.md")).thenReturn(true);
        var params = Map.<String, Object>of("id", "id1", "name", "Name", "promptFile", "hello.md", "nextRunAt",
                "invalid", "enabled", true);
        assertThat(tool.execute(params)).startsWith("Error:");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./mvnw test -Dtest=TaskUpdateToolTest -q 2>&1 | tail -10
```

Expected: compilation error.

- [ ] **Step 3: Implement TaskUpdateTool**

```java
// src/main/java/org/daneel/tool/TaskUpdateTool.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.PlannedTask;
import org.daneel.task.PromptCatalog;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskUpdateTool implements DaneelToolInterface {

    private final TaskStore taskStore;
    private final PromptCatalog promptCatalog;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "task_update";
    }

    @Override
    public String description() {
        return "Updates an existing planned task by id. All fields are replaced.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(
                new ToolProperty("id", "The task id to update", "string", true),
                new ToolProperty("name", "Human-readable task name", "string", true),
                new ToolProperty("promptFile",
                        "Filename of an existing prompt file",
                        "string", true),
                new ToolProperty("nextRunAt",
                        "New run time as an ISO-8601 UTC instant, e.g. 2026-06-02T15:00:00Z",
                        "string", true),
                new ToolProperty("recurringIntervalMinutes",
                        "Recurrence interval in minutes; omit for a one-shot task",
                        "integer", false),
                new ToolProperty("enabled",
                        "Whether the task is active",
                        "boolean", true)
        );
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        var idRaw = params.get("id");
        if (idRaw == null || idRaw.toString().isBlank()) {
            return "Error: id is required.";
        }
        var id = idRaw.toString();
        if (taskStore.findById(id).isEmpty()) {
            return "Error: task not found: " + id;
        }
        var nameRaw = params.get("name");
        if (nameRaw == null || nameRaw.toString().isBlank()) {
            return "Error: name is required.";
        }
        var promptFileRaw = params.get("promptFile");
        if (promptFileRaw == null || promptFileRaw.toString().isBlank()) {
            return "Error: promptFile is required.";
        }
        if (!promptCatalog.exists(promptFileRaw.toString())) {
            return "Error: prompt file not found: " + promptFileRaw;
        }
        var nextRunAtRaw = params.get("nextRunAt");
        if (nextRunAtRaw == null || nextRunAtRaw.toString().isBlank()) {
            return "Error: nextRunAt is required.";
        }
        Instant nextRunAt;
        try {
            nextRunAt = Instant.parse(nextRunAtRaw.toString());
        } catch (DateTimeParseException e) {
            return "Error: nextRunAt must be an ISO-8601 UTC instant, e.g. 2026-06-02T15:00:00Z.";
        }

        Integer interval = null;
        var intervalRaw = params.get("recurringIntervalMinutes");
        if (intervalRaw instanceof Number n) {
            interval = n.intValue();
        }

        boolean enabled = true;
        var enabledRaw = params.get("enabled");
        if (enabledRaw instanceof Boolean b) {
            enabled = b;
        } else if (enabledRaw != null) {
            enabled = Boolean.parseBoolean(enabledRaw.toString());
        }

        var updated = new PlannedTask(id, nameRaw.toString(),
                promptFileRaw.toString(), nextRunAt, interval, enabled);
        taskStore.save(updated);
        log.info("task_updated_by_llm id={}", id);
        return objectMapper.writeValueAsString(updated);
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -Dtest=TaskUpdateToolTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 5 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/tool/TaskUpdateTool.java \
        src/test/java/org/daneel/tool/TaskUpdateToolTest.java
git commit -m "feat: add TaskUpdateTool to update a scheduled task"
```

---

### Task 9: TaskDeleteTool

**Files:**

- Create: `src/main/java/org/daneel/tool/TaskDeleteTool.java`
- Create: `src/test/java/org/daneel/tool/TaskDeleteToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/tool/TaskDeleteToolTest.java
package org.daneel.tool;

import org.daneel.task.PlannedTask;
import org.daneel.task.TaskStore;
import org.daneel.tool.task.TaskDeleteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDeleteToolTest {

    @Mock
    private TaskStore taskStore;

    private TaskDeleteTool tool;

    @BeforeEach
    void setUp() {
        tool = new TaskDeleteTool(taskStore);
    }

    @Test
    void execute_deletesTask_returnsDone() {
        var task = new PlannedTask("id1", "Task", "hello.md", Instant.parse("2026-06-02T10:00:00Z"), null, true);
        when(taskStore.findById("id1")).thenReturn(Optional.of(task));
        var result = tool.execute(Map.of("id", "id1"));
        verify(taskStore).delete("id1");
        assertThat(result).isEqualTo("Deleted.");
    }

    @Test
    void execute_taskNotFound_returnsError() {
        when(taskStore.findById("x")).thenReturn(Optional.empty());
        assertThat(tool.execute(Map.of("id", "x"))).startsWith("Error:");
    }

    @Test
    void execute_missingId_returnsError() {
        var params = new HashMap<String, Object>();
        params.put("id", null);
        assertThat(tool.execute(params)).startsWith("Error:");
    }

    @Test
    void name_isTaskDelete() {
        assertThat(tool.name()).isEqualTo("task_delete");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./mvnw test -Dtest=TaskDeleteToolTest -q 2>&1 | tail -10
```

Expected: compilation error.

- [ ] **Step 3: Implement TaskDeleteTool**

```java
// src/main/java/org/daneel/tool/TaskDeleteTool.java
package org.daneel.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.TaskStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDeleteTool implements DaneelToolInterface {

    private final TaskStore taskStore;

    @Override
    public String name() {
        return "task_delete";
    }

    @Override
    public String description() {
        return "Deletes a planned task by id. The prompt file is not deleted.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of(new ToolProperty("id", "The task id to delete", "string", true));
    }

    @Override
    public String execute(Map<String, Object> params) {
        var raw = params.get("id");
        if (raw == null || raw.toString().isBlank()) {
            return "Error: id is required.";
        }
        var id = raw.toString();
        if (taskStore.findById(id).isEmpty()) {
            return "Error: task not found: " + id;
        }
        taskStore.delete(id);
        log.info("task_deleted_by_llm id={}", id);
        return "Deleted.";
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -Dtest=TaskDeleteToolTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/tool/TaskDeleteTool.java \
        src/test/java/org/daneel/tool/TaskDeleteToolTest.java
git commit -m "feat: add TaskDeleteTool to remove a scheduled task"
```

---

### Task 10: PromptListTool

**Files:**

- Create: `src/main/java/org/daneel/tool/PromptListTool.java`
- Create: `src/test/java/org/daneel/tool/PromptListToolTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/org/daneel/tool/PromptListToolTest.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.daneel.task.PromptCatalog;
import org.daneel.task.PromptSummary;
import org.daneel.tool.task.PromptListTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptListToolTest {

    @Mock
    private PromptCatalog promptCatalog;

    private PromptListTool tool;

    @BeforeEach
    void setUp() {
        tool = new PromptListTool(promptCatalog, new ObjectMapper());
    }

    @Test
    void name_isPromptList() {
        assertThat(tool.name()).isEqualTo("prompt_list");
    }

    @Test
    void properties_isEmpty() {
        assertThat(tool.properties()).isEmpty();
    }

    @Test
    void execute_returnsJsonArrayWithSummaries() {
        when(promptCatalog.list()).thenReturn(List.of(new PromptSummary("hello.md", "Says bonjour via speak tool.")));
        var result = tool.execute(Map.of());
        assertThat(result).contains("hello.md").contains("Says bonjour via speak tool.");
    }

    @Test
    void execute_whenEmpty_returnsEmptyJsonArray() {
        when(promptCatalog.list()).thenReturn(List.of());
        assertThat(tool.execute(Map.of())).isEqualTo("[]");
    }
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./mvnw test -Dtest=PromptListToolTest -q 2>&1 | tail -10
```

Expected: compilation error.

- [ ] **Step 3: Implement PromptListTool**

```java
// src/main/java/org/daneel/tool/PromptListTool.java
package org.daneel.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.daneel.task.PromptCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptListTool implements DaneelToolInterface {

    private final PromptCatalog promptCatalog;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "prompt_list";
    }

    @Override
    public String description() {
        return "Lists all available prompt files with their summaries. "
                + "Use this to discover which promptFile to use when creating or updating a task.";
    }

    @Override
    public List<ToolProperty> properties() {
        return List.of();
    }

    @Override
    @SneakyThrows
    public String execute(Map<String, Object> params) {
        return objectMapper.writeValueAsString(promptCatalog.list());
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./mvnw test -Dtest=PromptListToolTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/tool/PromptListTool.java \
        src/test/java/org/daneel/tool/PromptListToolTest.java
git commit -m "feat: add PromptListTool to discover available prompts and summaries"
```

---

### Task 11: Full suite + push

- [ ] **Step 1: Run the full test suite**

```bash
./mvnw test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS` — all tests pass. Count should be significantly higher than before this feature (was 50; now
should include 6 new test classes + extended PromptResolverTest).

- [ ] **Step 2: Push to remote**

```bash
git push origin main
```

---

## Verification (after implementation)

1. Start the app: `./start.sh`
2. In the chat UI: "What prompts are available?" → bot calls `prompt_list` → reports `hello.md` with its summary.
3. "Schedule the hello prompt to run in 2 minutes." → bot calls `task_create` → confirm entry appears in
   `tasks/tasks.yml` and at `GET /tasks`.
4. "List my tasks" → bot calls `task_list` → returns JSON of all tasks.
5. "Delete task `<id>`" → bot calls `task_delete` → task gone from `GET /tasks`.
