# SpeakTool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `SpeakTool` that runs a configurable system command to speak text aloud, defaulting to macOS `say -v Thomas {text}`.

**Architecture:** `SpeakTool` is a `@Component` implementing `DaneelToolInterface`. It receives a command template via a `@Value`-annotated constructor parameter (testable without Spring context). `execute` splits the template by spaces, replaces the `{text}` token with the actual text, and runs the result via `ProcessBuilder`. `application.yml` documents the property with the default value for discoverability.

**Tech Stack:** Java 24, Spring Boot 3.5.14, Lombok `@SneakyThrows`, JUnit 5 + AssertJ.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/org/daneel/tool/SpeakTool.java` | New tool: runs a system TTS command |
| Create | `src/test/java/org/daneel/tool/SpeakToolTest.java` | Unit tests using `echo {text}` as command |
| Modify | `src/main/resources/application.yml` | Add `daneel.tools.speak.command` property |

---

## Task 1: `SpeakTool` (TDD)

**Files:**
- Create: `src/test/java/org/daneel/tool/SpeakToolTest.java`
- Create: `src/main/java/org/daneel/tool/SpeakTool.java`

- [ ] **Step 1: Create test directory (if not already present)**

  ```bash
  mkdir -p src/test/java/org/daneel/tool
  ```

  The directory already exists from the previous tool tasks — this is a no-op.

- [ ] **Step 2: Write the failing tests**

  Create `src/test/java/org/daneel/tool/SpeakToolTest.java`:

  ```java
  package org.daneel.tool;

  import org.junit.jupiter.api.Test;

  import java.util.Map;

  import static org.assertj.core.api.Assertions.assertThat;

  class SpeakToolTest {

      // Uses "echo {text}" as command — cross-platform, exits 0, doesn't need a voice
      private final SpeakTool tool = new SpeakTool("echo {text}");

      @Test
      void execute_runsCommandAndReturnsDone() {
          assertThat(tool.execute(Map.of("text", "hello"))).isEqualTo("Done.");
      }

      @Test
      void execute_returnsErrorForMissingText() {
          assertThat(tool.execute(Map.of())).startsWith("Error:");
      }

      @Test
      void execute_returnsErrorForBlankText() {
          assertThat(tool.execute(Map.of("text", "   "))).startsWith("Error:");
      }

      @Test
      void name_isSpeak() {
          assertThat(tool.name()).isEqualTo("speak");
      }

      @Test
      void properties_hasOneRequiredTextParam() {
          var props = tool.properties();
          assertThat(props).hasSize(1);
          assertThat(props.getFirst().name()).isEqualTo("text");
          assertThat(props.getFirst().required()).isTrue();
      }
  }
  ```

- [ ] **Step 3: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=SpeakToolTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `SpeakTool` not found.

- [ ] **Step 4: Create `SpeakTool.java`**

  ```java
  package org.daneel.tool;

  import lombok.SneakyThrows;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.stereotype.Component;

  import java.util.Arrays;
  import java.util.List;
  import java.util.Map;

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
          var args = Arrays.stream(commandTemplate.split(" "))
                  .map(token -> token.replace("{text}", text))
                  .toArray(String[]::new);
          var exitCode = new ProcessBuilder(args).inheritIO().start().waitFor();
          return exitCode == 0 ? "Done." : "Error: command exited with code " + exitCode + ".";
      }
  }
  ```

  **Key design notes:**
  - `@Value` is on the constructor parameter (not a field), so tests can instantiate `new SpeakTool("echo {text}")` directly without a Spring context.
  - `commandTemplate.split(" ")` splits the template into tokens; `.replace("{text}", text)` replaces the placeholder token. Because each token becomes a separate `ProcessBuilder` argument, text with spaces is passed safely as one argument — no shell injection.
  - `@SneakyThrows` handles `IOException` and `InterruptedException` from `ProcessBuilder`, consistent with `ToolRegistrar`'s existing pattern.

- [ ] **Step 5: Run tests to confirm they pass**

  ```bash
  ./mvnw test -Dtest=SpeakToolTest -q 2>&1 | tail -5
  ```

  Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 6: Run the full suite to confirm nothing regressed**

  ```bash
  ./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -8
  ```

  Expected: `Tests run: 24, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/org/daneel/tool/SpeakTool.java \
          src/test/java/org/daneel/tool/SpeakToolTest.java
  git commit -m "feat: add SpeakTool for system text-to-speech"
  ```

---

## Task 2: Document property in `application.yml`

**Files:**
- Modify: `src/main/resources/application.yml`

The `@Value` default already covers the case where the property is absent. This task adds it to `application.yml` explicitly so it's discoverable and easy to override.

Current `application.yml`:

```yaml
spring:
  application:
    name: daneelclaw
  ai:
    openai:
      base-url: http://localhost:1234
      api-key: not-needed
      chat:
        options:
          model: ${LMSTUDIO_MODEL:local-model}

server:
  port: 8080
```

- [ ] **Step 1: Add the `daneel` block at the end of `application.yml`**

  Replace the file:

  ```yaml
  spring:
    application:
      name: daneelclaw
    ai:
      openai:
        base-url: http://localhost:1234
        api-key: not-needed
        chat:
          options:
            model: ${LMSTUDIO_MODEL:local-model}

  server:
    port: 8080

  daneel:
    tools:
      speak:
        command: "say -v Thomas {text}"
  ```

- [ ] **Step 2: Verify app starts without errors**

  ```bash
  ./mvnw spring-boot:run > /tmp/daneel-boot.log 2>&1 &
  APP_PID=$!
  sleep 14
  grep -c "Started DaneelclawApplication" /tmp/daneel-boot.log
  kill $APP_PID 2>/dev/null
  wait $APP_PID 2>/dev/null
  ```

  Expected: output is `1` (app started cleanly). If port 8080 is busy, run `lsof -ti:8080 | xargs kill -9` first, or use `./start.sh`.

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/resources/application.yml
  git commit -m "feat: document speak tool command property in application.yml"
  ```

---

## Self-Review

**Spec coverage:**
- `SpeakTool` with name `speak`, description, one required `text` property (Task 1) ✓
- `@Value("${daneel.tools.speak.command:say -v Thomas {text}}")` (Task 1) ✓
- Constructor injection for testability (Task 1) ✓
- Null/blank guard returning "Error:..." (Task 1, tests cover it) ✓
- `ProcessBuilder` array split + token replacement (Task 1) ✓
- `@SneakyThrows` (Task 1) ✓
- `application.yml` property (Task 2) ✓
- 5 tests including both error paths (Task 1) ✓

**Placeholders:** None.

**Type consistency:**
- `SpeakTool("echo {text}")` test constructor matches `public SpeakTool(@Value(...) String commandTemplate)`
- `tool.execute(Map.of("text", "hello"))` matches `execute(Map<String, Object> params)` — `Map.of` returns `Map<String, Object>` ✓
- `props.getFirst()` requires Java 21+ (project uses Java 24) ✓
