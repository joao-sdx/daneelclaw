# Tool Framework Design

**Date:** 2026-06-01
**Status:** Approved

## Context

The chatbot currently calls LMStudio with a static system prompt and conversation history. This spec
adds a lightweight tool framework so DaneelClaw can execute real-world actions: a custom
`DaneelToolInterface` decouples tool implementations from Spring AI internals, an adapter bridges
them to Spring AI's `ToolCallback` system, and two concrete tools (`TimeProviderTool`,
`LocalTimezoneTool`) prove the integration.

## Package

All new code lives under `org.daneel.tool`. Existing code in `org.daneel.chat` is unchanged except
for `ChatConfig`, which gains a `ToolRegistrar` dependency.

## Types

### `ToolProperty` record

Describes one input parameter a tool accepts.

```java
package org.daneel.tool;

public record ToolProperty(String name, String description, String type, boolean required) {}
```

- `type` is a JSON Schema primitive type string: `"string"`, `"number"`, `"boolean"`, etc.
- Tools with no parameters return an empty list from `properties()`.

### `DaneelToolInterface`

The contract every tool must implement.

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

- `name()` — unique, snake_case identifier used by the LLM to call the tool.
- `description()` — natural-language description sent to the LLM in the tool spec.
- `properties()` — list of input parameters; empty list for no-parameter tools.
- `execute(params)` — receives the parsed parameter map (String key → value), returns the result
  as a plain string. The framework handles JSON parsing before calling this.

### `ToolRegistrar`

`@Component` that:
1. Receives all `DaneelToolInterface` beans via `List<DaneelToolInterface>` constructor injection.
2. Converts each to a Spring AI `FunctionToolCallback` using `FunctionToolCallback.builder`.
3. Exposes a `ToolCallback[] getCallbacks()` method for `ChatConfig` to consume.

**JSON schema generation** (done inside `ToolRegistrar`):

For each tool, builds a JSON Schema string from `properties()`:

```json
{
  "type": "object",
  "properties": {
    "<name>": { "type": "<type>", "description": "<description>" }
  },
  "required": ["<name of required props>"]
}
```

No-parameter tools get: `{"type":"object","properties":{}}`.

**Adapter per tool**: creates a `FunctionToolCallback` that:
1. Receives the raw JSON string from Spring AI.
2. Parses it to `Map<String, Object>` using Jackson `ObjectMapper`.
3. Calls `tool.execute(map)`.
4. Returns the string result directly to the LLM.

`ObjectMapper` is injected (auto-configured by Spring Boot).

### `TimeProviderTool`

```
name:        time_provider
description: Returns the current date and time for a given timezone.
properties:  [ToolProperty("timezone", "IANA timezone identifier (e.g. America/New_York, Europe/London)", "string", true)]
execute:     ZonedDateTime.now(ZoneId.of(params.get("timezone").toString()))
             formatted as ISO-8601: DateTimeFormatter.ISO_ZONED_DATE_TIME
```

If the timezone string is invalid, `ZoneId.of()` throws `ZoneRulesException` — let it propagate;
Spring AI catches tool exceptions and reports them to the LLM.

### `LocalTimezoneTool`

```
name:        local_timezone
description: Returns the local system timezone identifier.
properties:  [] (no parameters)
execute:     ZoneId.systemDefault().getId()
```

## Wiring

`ChatConfig` gains a constructor parameter `ToolRegistrar toolRegistrar` and adds one call:

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder,
                      @Value("classpath:system-prompt.md") Resource systemPrompt,
                      ToolRegistrar toolRegistrar) {
    return builder
            .defaultSystem(systemPrompt)
            .defaultToolCallbacks(toolRegistrar.getCallbacks())
            .build();
}
```

All `DaneelToolInterface` beans are auto-discovered by Spring (they are `@Component`s in the same
package, covered by `@SpringBootApplication`'s component scan).

## Error handling

- Invalid timezone in `TimeProviderTool`: `ZoneRulesException` propagates through
  `ToolExecutionException` to the LLM, which can report it to the user.
- `ToolRegistrar` with zero tools: `getCallbacks()` returns an empty array; Spring AI handles this
  gracefully (no tools declared to the LLM).
- Missing `ObjectMapper`: Spring Boot auto-configures one; `ToolRegistrar` depends on it.

## Testing

- `ToolRegistrarTest`: creates two mock tools, verifies the callback array length and that each
  callback name matches the tool's `name()`. Uses a real `ObjectMapper`.
- `TimeProviderToolTest`: calls `execute({"timezone":"UTC"})` and verifies the result contains
  "UTC" and a parseable ISO-8601 datetime.
- `LocalTimezoneToolTest`: calls `execute({})` and verifies the result equals
  `ZoneId.systemDefault().getId()`.

## Files

| Action | Path |
|--------|------|
| Create | `src/main/java/org/daneel/tool/ToolProperty.java` |
| Create | `src/main/java/org/daneel/tool/DaneelToolInterface.java` |
| Create | `src/main/java/org/daneel/tool/ToolRegistrar.java` |
| Create | `src/main/java/org/daneel/tool/TimeProviderTool.java` |
| Create | `src/main/java/org/daneel/tool/LocalTimezoneTool.java` |
| Modify | `src/main/java/org/daneel/chat/ChatConfig.java` |
| Create | `src/test/java/org/daneel/tool/ToolRegistrarTest.java` |
| Create | `src/test/java/org/daneel/tool/TimeProviderToolTest.java` |
| Create | `src/test/java/org/daneel/tool/LocalTimezoneToolTest.java` |

## Verification

1. `./mvnw test` — all 5 existing tests still pass, plus the 3 new tool tests (8 total).
2. `./start.sh` — app starts; logs show no errors.
3. With LMStudio running, ask: "What time is it in Tokyo?" — DaneelClaw calls `time_provider` with
   `timezone=Asia/Tokyo` and returns the current local time.
4. Ask: "What is my local timezone?" — DaneelClaw calls `local_timezone` and returns the system
   timezone.

## Out of scope

- Tool authentication or per-user tool permissions.
- Async tool execution.
- Tool results streaming.
- More than two tools (more can be added later by implementing `DaneelToolInterface` and annotating
  with `@Component`).
