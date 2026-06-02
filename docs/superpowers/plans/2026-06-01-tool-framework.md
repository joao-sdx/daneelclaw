# Tool Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `DaneelToolInterface` abstraction, a `ToolRegistrar` adapter to Spring AI, and two concrete tools (`TimeProviderTool`, `LocalTimezoneTool`), wired into `ChatConfig` so every chat request automatically has the tools available.

**Architecture:** Each tool is a `@Component` implementing `DaneelToolInterface` (name, description, properties list, execute). `ToolRegistrar` collects all such beans, converts them to Spring AI `ToolCallback` instances by building a JSON schema from `properties()` and wrapping `execute` in a `call(String json)` that parses the JSON then delegates. `ChatConfig` injects `ToolRegistrar` and calls `builder.defaultToolCallbacks(...)`.

**Tech Stack:** Spring Boot 3.5.14, Spring AI 1.0.0 (`ToolCallback`, `ToolDefinition`), Lombok, Jackson `ObjectMapper`, JUnit 5 + AssertJ.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/org/daneel/tool/ToolProperty.java` | Record: parameter descriptor |
| Create | `src/main/java/org/daneel/tool/DaneelToolInterface.java` | Interface every tool must implement |
| Create | `src/main/java/org/daneel/tool/ToolRegistrar.java` | Converts `DaneelToolInterface` list → `ToolCallback[]` |
| Create | `src/main/java/org/daneel/tool/TimeProviderTool.java` | Tool: datetime for a given timezone |
| Create | `src/main/java/org/daneel/tool/LocalTimezoneTool.java` | Tool: local system timezone |
| Modify | `src/main/java/org/daneel/chat/ChatConfig.java` | Wire `ToolRegistrar` into `ChatClient` builder |
| Create | `src/test/java/org/daneel/tool/ToolRegistrarTest.java` | Unit tests for adapter logic |
| Create | `src/test/java/org/daneel/tool/TimeProviderToolTest.java` | Unit tests for TimeProviderTool |
| Create | `src/test/java/org/daneel/tool/LocalTimezoneToolTest.java` | Unit tests for LocalTimezoneTool |

---

## Task 1: Core abstractions — `ToolProperty` and `DaneelToolInterface`

These are pure Java types; no tests needed (no logic to test).

**Files:**
- Create: `src/main/java/org/daneel/tool/ToolProperty.java`
- Create: `src/main/java/org/daneel/tool/DaneelToolInterface.java`

- [ ] **Step 1: Create `ToolProperty.java`**

  ```java
  package org.daneel.tool;

  public record ToolProperty(String name, String description, String type, boolean required) {}
  ```

- [ ] **Step 2: Create `DaneelToolInterface.java`**

  ```java
  package org.daneel.tool;

  import java.util.List;
  import java.util.Map;

  public interface DaneelToolInterface {
      String name();
      String description();
      List<ToolProperty> properties();
      String execute(Map<String, Object> params);
  }
  ```

- [ ] **Step 3: Verify compilation**

  ```bash
  ./mvnw compile -q
  ```

  Expected: success.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/org/daneel/tool/ToolProperty.java \
          src/main/java/org/daneel/tool/DaneelToolInterface.java
  git commit -m "feat: add DaneelToolInterface and ToolProperty"
  ```

---

## Task 2: `ToolRegistrar` — adapter from DaneelToolInterface to Spring AI ToolCallback (TDD)

**Files:**
- Create: `src/test/java/org/daneel/tool/ToolRegistrarTest.java`
- Create: `src/main/java/org/daneel/tool/ToolRegistrar.java`

- [ ] **Step 1: Write the failing tests**

  Create `src/test/java/org/daneel/tool/ToolRegistrarTest.java`:

  ```java
  package org.daneel.tool;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.junit.jupiter.api.Test;

  import java.util.List;
  import java.util.Map;

  import static org.assertj.core.api.Assertions.assertThat;

  class ToolRegistrarTest {

      private final ObjectMapper objectMapper = new ObjectMapper();

      @Test
      void getCallbacks_returnsOneCallbackPerTool() {
          var tool1 = new StubTool("tool_one", "Tool One", List.of(), "result_one");
          var tool2 = new StubTool("tool_two", "Tool Two", List.of(), "result_two");
          var registrar = new ToolRegistrar(List.of(tool1, tool2), objectMapper);

          var callbacks = registrar.getCallbacks();

          assertThat(callbacks).hasSize(2);
          assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("tool_one");
          assertThat(callbacks[1].getToolDefinition().name()).isEqualTo("tool_two");
      }

      @Test
      void getCallbacks_callDelegatesExecute() throws Exception {
          var tool = new StubTool("my_tool", "My tool", List.of(), "hello");
          var registrar = new ToolRegistrar(List.of(tool), objectMapper);

          var result = registrar.getCallbacks()[0].call("{}");

          assertThat(result).isEqualTo("hello");
      }

      @Test
      void getCallbacks_emptyTools_returnsEmptyArray() {
          var registrar = new ToolRegistrar(List.of(), objectMapper);

          assertThat(registrar.getCallbacks()).isEmpty();
      }

      @Test
      void getCallbacks_schemaIncludesRequiredProperty() {
          var prop = new ToolProperty("timezone", "IANA timezone", "string", true);
          var tool = new StubTool("tz_tool", "TZ tool", List.of(prop), "UTC");
          var registrar = new ToolRegistrar(List.of(tool), objectMapper);

          var schema = registrar.getCallbacks()[0].getToolDefinition().inputSchema();

          assertThat(schema).contains("\"timezone\"");
          assertThat(schema).contains("\"required\"");
      }

      private record StubTool(String name, String description,
                               List<ToolProperty> properties,
                               String result) implements DaneelToolInterface {
          @Override
          public String execute(Map<String, Object> params) {
              return result;
          }
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=ToolRegistrarTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `ToolRegistrar` not found.

- [ ] **Step 3: Create `ToolRegistrar.java`**

  ```java
  package org.daneel.tool;

  import com.fasterxml.jackson.core.type.TypeReference;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import lombok.RequiredArgsConstructor;
  import lombok.SneakyThrows;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.ai.tool.ToolCallback;
  import org.springframework.ai.tool.definition.ToolDefinition;
  import org.springframework.stereotype.Component;

  import java.util.List;
  import java.util.Map;
  import java.util.stream.Collectors;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class ToolRegistrar {

      private final List<DaneelToolInterface> tools;
      private final ObjectMapper objectMapper;

      public ToolCallback[] getCallbacks() {
          return tools.stream()
                  .map(this::toCallback)
                  .toArray(ToolCallback[]::new);
      }

      private ToolCallback toCallback(DaneelToolInterface tool) {
          var schema = buildSchema(tool.properties());
          var definition = ToolDefinition.builder()
                  .name(tool.name())
                  .description(tool.description())
                  .inputSchema(schema)
                  .build();
          return new ToolCallback() {
              @Override
              public ToolDefinition getToolDefinition() {
                  return definition;
              }

              @Override
              @SneakyThrows
              public String call(String toolInput) {
                  Map<String, Object> params = objectMapper.readValue(
                          toolInput, new TypeReference<>() {});
                  log.info("tool_call name={} params={}", tool.name(), params);
                  return tool.execute(params);
              }
          };
      }

      private String buildSchema(List<ToolProperty> properties) {
          if (properties.isEmpty()) {
              return "{\"type\":\"object\",\"properties\":{}}";
          }
          var props = properties.stream()
                  .map(p -> "\"" + p.name() + "\":{\"type\":\"" + p.type()
                          + "\",\"description\":\"" + p.description() + "\"}")
                  .collect(Collectors.joining(","));
          var required = properties.stream()
                  .filter(ToolProperty::required)
                  .map(p -> "\"" + p.name() + "\"")
                  .collect(Collectors.joining(","));
          var schema = new StringBuilder("{\"type\":\"object\",\"properties\":{")
                  .append(props)
                  .append("}");
          if (!required.isEmpty()) {
              schema.append(",\"required\":[").append(required).append("]");
          }
          return schema.append("}").toString();
      }
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mvnw test -Dtest=ToolRegistrarTest -q 2>&1 | tail -5
  ```

  Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/org/daneel/tool/ToolRegistrar.java \
          src/test/java/org/daneel/tool/ToolRegistrarTest.java
  git commit -m "feat: add ToolRegistrar to bridge DaneelToolInterface to Spring AI"
  ```

---

## Task 3: `TimeProviderTool` (TDD)

**Files:**
- Create: `src/test/java/org/daneel/tool/TimeProviderToolTest.java`
- Create: `src/main/java/org/daneel/tool/TimeProviderTool.java`

- [ ] **Step 1: Write the failing tests**

  Create `src/test/java/org/daneel/tool/TimeProviderToolTest.java`:

  ```java
  package org.daneel.tool;

  import org.junit.jupiter.api.Test;

  import java.time.ZoneId;
  import java.time.ZonedDateTime;
  import java.time.format.DateTimeFormatter;
  import java.util.Map;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;

  class TimeProviderToolTest {

      private final TimeProviderTool tool = new TimeProviderTool();

      @Test
      void execute_returnsIso8601DateTimeForUtc() {
          var result = tool.execute(Map.of("timezone", "UTC"));
          var parsed = ZonedDateTime.parse(result, DateTimeFormatter.ISO_ZONED_DATE_TIME);
          assertThat(parsed.getZone().getId()).isEqualTo("UTC");
      }

      @Test
      void execute_returnsDateTimeForTokyoTimezone() {
          var result = tool.execute(Map.of("timezone", "Asia/Tokyo"));
          var parsed = ZonedDateTime.parse(result, DateTimeFormatter.ISO_ZONED_DATE_TIME);
          assertThat(parsed.getZone().getId()).isEqualTo("Asia/Tokyo");
      }

      @Test
      void execute_throwsForInvalidTimezone() {
          assertThatThrownBy(() -> tool.execute(Map.of("timezone", "Not/A/Zone")))
                  .isInstanceOf(Exception.class);
      }

      @Test
      void name_isTimeProvider() {
          assertThat(tool.name()).isEqualTo("time_provider");
      }

      @Test
      void properties_hasOneRequiredTimezoneParam() {
          var props = tool.properties();
          assertThat(props).hasSize(1);
          assertThat(props.getFirst().name()).isEqualTo("timezone");
          assertThat(props.getFirst().type()).isEqualTo("string");
          assertThat(props.getFirst().required()).isTrue();
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=TimeProviderToolTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `TimeProviderTool` not found.

- [ ] **Step 3: Create `TimeProviderTool.java`**

  ```java
  package org.daneel.tool;

  import org.springframework.stereotype.Component;

  import java.time.ZoneId;
  import java.time.ZonedDateTime;
  import java.time.format.DateTimeFormatter;
  import java.util.List;
  import java.util.Map;

  @Component
  public class TimeProviderTool implements DaneelToolInterface {

      @Override
      public String name() {
          return "time_provider";
      }

      @Override
      public String description() {
          return "Returns the current date and time for a given timezone.";
      }

      @Override
      public List<ToolProperty> properties() {
          return List.of(new ToolProperty(
                  "timezone",
                  "IANA timezone identifier (e.g. America/New_York, Europe/London)",
                  "string",
                  true));
      }

      @Override
      public String execute(Map<String, Object> params) {
          var timezone = params.get("timezone").toString();
          return ZonedDateTime.now(ZoneId.of(timezone))
                  .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
      }
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mvnw test -Dtest=TimeProviderToolTest -q 2>&1 | tail -5
  ```

  Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/org/daneel/tool/TimeProviderTool.java \
          src/test/java/org/daneel/tool/TimeProviderToolTest.java
  git commit -m "feat: add TimeProviderTool"
  ```

---

## Task 4: `LocalTimezoneTool` (TDD)

**Files:**
- Create: `src/test/java/org/daneel/tool/LocalTimezoneToolTest.java`
- Create: `src/main/java/org/daneel/tool/LocalTimezoneTool.java`

- [ ] **Step 1: Write the failing tests**

  Create `src/test/java/org/daneel/tool/LocalTimezoneToolTest.java`:

  ```java
  package org.daneel.tool;

  import org.junit.jupiter.api.Test;

  import java.time.ZoneId;
  import java.util.Map;

  import static org.assertj.core.api.Assertions.assertThat;

  class LocalTimezoneToolTest {

      private final LocalTimezoneTool tool = new LocalTimezoneTool();

      @Test
      void execute_returnsSystemTimezone() {
          assertThat(tool.execute(Map.of())).isEqualTo(ZoneId.systemDefault().getId());
      }

      @Test
      void name_isLocalTimezone() {
          assertThat(tool.name()).isEqualTo("local_timezone");
      }

      @Test
      void description_isNotBlank() {
          assertThat(tool.description()).isNotBlank();
      }

      @Test
      void properties_isEmpty() {
          assertThat(tool.properties()).isEmpty();
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=LocalTimezoneToolTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `LocalTimezoneTool` not found.

- [ ] **Step 3: Create `LocalTimezoneTool.java`**

  ```java
  package org.daneel.tool;

  import org.springframework.stereotype.Component;

  import java.time.ZoneId;
  import java.util.List;
  import java.util.Map;

  @Component
  public class LocalTimezoneTool implements DaneelToolInterface {

      @Override
      public String name() {
          return "local_timezone";
      }

      @Override
      public String description() {
          return "Returns the local system timezone identifier.";
      }

      @Override
      public List<ToolProperty> properties() {
          return List.of();
      }

      @Override
      public String execute(Map<String, Object> params) {
          return ZoneId.systemDefault().getId();
      }
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mvnw test -Dtest=LocalTimezoneToolTest -q 2>&1 | tail -5
  ```

  Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/org/daneel/tool/LocalTimezoneTool.java \
          src/test/java/org/daneel/tool/LocalTimezoneToolTest.java
  git commit -m "feat: add LocalTimezoneTool"
  ```

---

## Task 5: Wire `ToolRegistrar` into `ChatConfig`

**Files:**
- Modify: `src/main/java/org/daneel/chat/ChatConfig.java`

The current `ChatConfig` builds the `ChatClient` without tools. Add `ToolRegistrar` as a third
constructor parameter and call `builder.defaultToolCallbacks(toolRegistrar.getCallbacks())`.

- [ ] **Step 1: Update `ChatConfig.java`**

  Replace the file:

  ```java
  package org.daneel.chat;

  import org.daneel.tool.ToolRegistrar;
  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.boot.web.client.RestClientCustomizer;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.core.io.Resource;
  import org.springframework.http.client.JdkClientHttpRequestFactory;

  import java.net.http.HttpClient;
  import java.time.Duration;

  @Configuration
  class ChatConfig {

      @Bean
      ChatClient chatClient(ChatClient.Builder builder,
                            @Value("classpath:system-prompt.md") Resource systemPrompt,
                            ToolRegistrar toolRegistrar) {
          return builder
                  .defaultSystem(systemPrompt)
                  .defaultToolCallbacks(toolRegistrar.getCallbacks())
                  .build();
      }

      @Bean
      RestClientCustomizer http11RestClientCustomizer() {
          var httpClient = HttpClient.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
                  .connectTimeout(Duration.ofSeconds(10))
                  .build();
          var factory = new JdkClientHttpRequestFactory(httpClient);
          factory.setReadTimeout(Duration.ofMinutes(2));
          return builder -> builder.requestFactory(factory);
      }
  }
  ```

- [ ] **Step 2: Run the full test suite**

  ```bash
  ./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -10
  ```

  Expected:
  - `Tests run: 4, Failures: 0` — ToolRegistrarTest
  - `Tests run: 5, Failures: 0` — TimeProviderToolTest
  - `Tests run: 4, Failures: 0` — LocalTimezoneToolTest
  - `Tests run: 3, Failures: 0` — ChatServiceTest
  - `Tests run: 2, Failures: 0` — ChatControllerTest
  - Total: `Tests run: 18, Failures: 0, Errors: 0` and `BUILD SUCCESS`

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/java/org/daneel/chat/ChatConfig.java
  git commit -m "feat: wire ToolRegistrar into ChatClient default tool callbacks"
  ```

---

## Self-Review

**Spec coverage:**
- `ToolProperty` record (Task 1) ✓
- `DaneelToolInterface` with `name()`, `description()`, `properties()`, `execute(Map)` (Task 1) ✓
- `ToolRegistrar` collects beans, builds JSON schema, wraps in `ToolCallback` (Task 2) ✓
- `TimeProviderTool`: name, description, one required `timezone` property, ISO-8601 result (Task 3) ✓
- `LocalTimezoneTool`: name, description, empty properties, returns system timezone (Task 4) ✓
- `ChatConfig` wired with `defaultToolCallbacks` (Task 5) ✓
- Tests: ToolRegistrarTest (4), TimeProviderToolTest (5), LocalTimezoneToolTest (4) ✓
- Existing 5 tests unaffected ✓

**Placeholder scan:** None found.

**Type consistency:**
- `DaneelToolInterface.execute(Map<String, Object>)` → used consistently in `ToolRegistrar.toCallback()` and in both tool `execute()` method signatures
- `ToolDefinition.builder().name(...).description(...).inputSchema(...).build()` → consistent in `ToolRegistrar`
- `toolRegistrar.getCallbacks()` → defined in `ToolRegistrar` as `ToolCallback[] getCallbacks()`, used in `ChatConfig` as `defaultToolCallbacks(...)`
- `ToolProperty(name, description, type, required)` constructor order → consistent across `TimeProviderTool.properties()` and test assertions
