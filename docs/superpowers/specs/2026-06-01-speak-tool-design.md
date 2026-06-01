# SpeakTool Design

**Date:** 2026-06-01
**Status:** Approved

## Context

DaneelClaw can already call tools for time and timezone. This spec adds a `SpeakTool` that lets
the bot speak text aloud by running a configurable system command. The command template is set in
`application.yml` and defaults to macOS `say` with the Thomas (French) voice.

## Files

| Action | Path |
|--------|------|
| Create | `src/main/java/org/daneel/tool/SpeakTool.java` |
| Modify | `src/main/resources/application.yml` |
| Create | `src/test/java/org/daneel/tool/SpeakToolTest.java` |

## Tool definition

```
name:        speak
description: Speaks text aloud using the system's text-to-speech command.
properties:  [ToolProperty("text", "The text to speak aloud", "string", true)]
```

## Configuration

Property: `daneel.tools.speak.command`
Default: `say -v Thomas {text}`

On macOS, `say -v Thomas` uses the built-in Thomas voice (French). The placeholder `{text}` marks
where the text argument is injected. It must appear exactly once in the template.

`application.yml` entry (explicit, for discoverability — the `@Value` default already covers it):

```yaml
daneel:
  tools:
    speak:
      command: "say -v Thomas {text}"
```

## Implementation

`SpeakTool` is a `@Component` implementing `DaneelToolInterface`.

**Injection:**
```java
@Value("${daneel.tools.speak.command:say -v Thomas {text}}")
private String commandTemplate;
```

**`execute(Map<String, Object> params)`:**

1. Extract `params.get("text")`. If null or blank → return `"Error: text parameter is required."`.
2. Split `commandTemplate` by single space into a `String[]`.
3. Replace any token equal to `{text}` with the actual text string (each token becomes one
   `ProcessBuilder` argument — no shell interpolation, so the text is safe even if it contains
   spaces or special characters).
4. Run `new ProcessBuilder(args).inheritIO().start().waitFor()`.
5. On exit code 0 → return `"Done."`.
6. On non-zero exit → return `"Error: command exited with code N."`.

**Security note:** Because ProcessBuilder receives an argument array (not a shell command string),
there is no shell injection risk. The text is passed as one discrete argument to the process.

**Error handling:** `IOException` and `InterruptedException` from `ProcessBuilder` propagate via
`@SneakyThrows` (consistent with `ToolRegistrar`), letting Spring AI surface them as tool errors.

## Testing

Unit tests use `echo {text}` as the command template (cross-platform, always exits 0):

- `execute_runsCommandAndReturnsDone` — calls with `{"text": "hello"}`, expects `"Done."`.
- `execute_returnsErrorForMissingText` — calls with `{}`, expects string starting with `"Error:"`.
- `name_isSpeak` — asserts `name()` returns `"speak"`.
- `properties_hasOneRequiredTextParam` — asserts one property, name `"text"`, required.

## Verification

1. `./mvnw test` — all 23 tests pass (19 existing + 4 new).
2. On macOS with LMStudio running: ask DaneelClaw to say something in French — it should invoke
   the `speak` tool and Thomas's voice is heard.
3. Override the command in `application.yml` (e.g. `say -v Fiona {text}`) and restart — confirms
   the property drives the command.

## Out of scope

- Async/non-blocking execution.
- Multiple placeholder occurrences.
- Platform detection or fallback voices.
