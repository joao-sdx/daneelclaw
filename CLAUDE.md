# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package

# Run (recommended — kills existing port 8080 first)
./start.sh

# Run tests
./mvnw test
./mvnw test -Dtest=ChatServiceTest                              # single class
./mvnw test -Dtest=ChatServiceTest#chat_returnsReplyWithNullAction  # single method

# Format code (Google Java Format via Spotless)
./mvnw spotless:apply
```

The app talks to LMStudio at `http://localhost:1234`. The model is configured via `LMSTUDIO_MODEL` env var, defaulting to `nvidia/nemotron-3-nano-4b`. LMStudio must be running with a model loaded for the app to function.

## Architecture

DaneelClaw is a personal AI assistant: a chat interface backed by a local LLM (via LMStudio / Spring AI's OpenAI-compatible client), with a task scheduler that fires LLM-driven automations on a cron-like schedule.

**Three subsystems:**

### Chat (`org.daneel.chat`)

`ChatController` accepts `POST /chat` with `{sessionId, message}`. `ChatService` maintains per-session conversation history in a `ConcurrentHashMap` and calls Spring AI's `ChatClient` with the full history and all registered tool callbacks. History is auto-compacted when it exceeds 8000 characters (configurable): the `summaryChatClient` bean (tool-free) produces a summary injected as a `SystemMessage`, keeping only the last 4 messages. `/clear` wipes history; `/compact` forces immediate compaction.

`ChatConfig` wires two `ChatClient` beans: the main one (has tools) and `summaryChatClient` (no tools, used for compaction).

### Task Scheduling (`org.daneel.task`)

`TaskPoller` runs every 60 seconds (`@Scheduled`). It reads `./tasks/tasks.yml` via `TaskStore` and fires any enabled `PlannedTask` whose `nextRunAt` is in the past. Each task has a `promptFile` pointing to a `.md` file in `./tasks/`; `PromptResolver` loads it and substitutes `{trigger_time_gmt}`, `{current_time_gmt}`, etc. The resolved prompt is sent to `ChatService` with a synthetic session ID (`auto-<taskId>-<epoch>`). After execution, recurring tasks get their `nextRunAt` advanced; one-shot tasks are disabled.

`TaskStore` persists to YAML using atomic temp-file writes. `TaskController` exposes full CRUD at `/tasks` and `/tasks/{id}`.

`PromptCatalog` discovers `.md` files in `./tasks/` and reads YAML frontmatter (via `PromptDocument`) for a `summary` metadata field.

### Tool System (`org.daneel.tool`)

`DaneelToolInterface` is the contract for all tools: name, description, `List<ToolProperty>`, and an `execute(Map<String,Object>)` method. `ToolRegistrar` is a `@Component` that collects all `DaneelToolInterface` beans at startup, generates a JSON schema from their `ToolProperty` metadata, and produces a `ToolCallback[]` array that `ChatConfig` passes to the main `ChatClient`.

Implemented tools: `TaskCreateTool`, `TaskUpdateTool`, `TaskDeleteTool`, `TaskListTool`, `TaskGetTool`, `PromptListTool`, `PromptCreateTool`, `TimeProviderTool`, `LocalTimezoneTool`, `SpeakTool` (macOS `say` command), `SpawnPerItemTool` (fan-out: spawns one background sub-run per item in a list).

**Adding a new tool:** implement `DaneelToolInterface`, annotate with `@Component`, define your `ToolProperty` list. `ToolRegistrar` picks it up automatically.

## Key Configuration

`src/main/resources/application.yml`:
- `daneel.tools.speak.command` — TTS command (`say -v Thomas {text}` on macOS)
- `daneel.scheduler.tasks-dir` — directory for task YAML and prompt `.md` files (`./tasks`)
- `daneel.scheduler.check-interval-ms` — polling interval (default 60000)
- `daneel.chat.compact.char-threshold` — auto-compaction threshold (default 8000)
- `daneel.chat.compact.keep-last-messages` — messages retained after compaction (default 4)
- `daneel.telegram.enabled` — set via `TELEGRAM_ENABLED=true` env var to activate the Telegram channel (default `false`)
- `daneel.telegram.bot-token` — set via `TELEGRAM_BOT_TOKEN` env var; required when enabled
- `daneel.telegram.allowed-chat-ids` — set via `TELEGRAM_ALLOWED_CHAT_IDS=<id1>,<id2>` env var; if empty, all chats allowed
- `daneel.telegram.poll-delay-ms` — polling interval in ms (default 1000)

`src/main/resources/system-prompt.md` — the LLM system prompt (currently French-language, concise/friendly persona).

## Testing Patterns

- `ChatServiceTest` mocks `ChatClient` with `Answers.RETURNS_DEEP_STUBS` and uses low thresholds (200 chars, keep 2) to exercise compaction logic without huge histories.
- `TaskStoreTest` uses JUnit 5 `@TempDir` for isolated file I/O.
- Tool tests mock `TaskStore` / `PromptCatalog` and verify with `ArgumentCaptor`.
- `PromptCreateToolTest` is known-flaky when run in suite (timing-sensitive filename generation); passes reliably in isolation.

## Apache Camel

Three Camel routes are active:
- `TaskPollRoute` (`task-poll`) — timer-driven, splits due tasks, calls `TaskPoller.run` per task.
- `FanOutRoute` (`fan-out`) — `seda:fanout` consumer (1 worker), calls `FanOutRunner.run` per item enqueued by `SpawnPerItemTool`.
- `TelegramRoute` (`telegram-inbound`) — long-polls Telegram for text messages; only registered when `daneel.telegram.enabled=true`. Filters by allowlist, delegates to `TelegramRunner` which calls `ChatService.chat("tg-<chatId>", text)`, and sends the reply back (chunked to ≤4096 chars per Telegram's limit).
