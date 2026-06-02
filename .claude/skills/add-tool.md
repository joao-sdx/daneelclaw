---
name: add-tool
description: Use when adding a new bot capability to DaneelClaw — a tool the LLM can invoke during chat. Triggers on requests like "add a tool that...", "implement a new tool", "give the bot the ability to...".
---

# add-tool

## Overview

DaneelClaw has a pluggable tool system. Every `@Component` implementing `DaneelToolInterface` is
auto-discovered by `ToolRegistrar` and registered with the Spring AI ChatClient. No manual wiring needed.

Key files:
- Interface: `src/main/java/org/daneel/tool/DaneelToolInterface.java`
- Example: `src/main/java/org/daneel/tool/SpeakTool.java`
- Config: `src/main/resources/application.yml` (for `@Value` properties)
- Tests: `src/test/java/org/daneel/tool/`

## Steps

1. **Read the interface** — check `DaneelToolInterface.java` for the exact contract
2. **Plan the tool** — state the tool name (snake_case), description, properties list, and what `execute()` does. Confirm with the user before writing code.
3. **Write the tool class** in `src/main/java/org/daneel/tool/`:
   - `@Component`, `@Slf4j`, `@RequiredArgsConstructor` (if needed)
   - Implement `name()`, `description()`, `properties()`, `execute(Map<String,Object> params)`
   - Use `@Value("${...}")` for configurable values; provide a sensible default
   - Null/blank-guard every optional param — return `"Error: X parameter is required."` rather than throwing NPE
   - Never use `System.out.println` — use `log.info` / `log.error`
4. **Add config** to `application.yml` under `daneel.tools.<toolname>.*` if the tool needs external config
5. **Write a unit test** in `src/test/java/org/daneel/tool/<ToolName>Test.java`:
   - Test the happy path
   - Test missing/null required params
   - Test any relevant edge cases
6. **Run tests** — `./mvnw test -pl . -Dtest=<ToolName>Test` must pass
7. **Run full suite** — `./mvnw test` must still be green

## Guardrails

- Tool name must be unique — check existing tools before naming
- `properties()` entries must accurately describe param types so the LLM generates correct JSON
- Required params that are missing → return error string, never throw from `execute()`
- Config keys go under `daneel.tools.*` namespace (consistent with SpeakTool)
- License headers are managed by `task update-license` — never write them manually