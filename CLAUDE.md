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

`ChatConfig` wires three `ChatClient` beans: the main one (no default tools — tools are bound per-request), `summaryChatClient` (no tools, used for compaction), and `toolSelectorChatClient` (no tools, used for per-message tool selection).

### Task Scheduling (`org.daneel.task`)

`TaskPoller` runs every 60 seconds (`@Scheduled`). It reads `./tasks/tasks.yml` via `TaskStore` and fires any enabled `PlannedTask` whose `nextRunAt` is in the past. Each task has a `promptFile` pointing to a `.md` file in `./tasks/`; `PromptResolver` loads it and substitutes `{trigger_time_gmt}`, `{current_time_gmt}`, etc. The resolved prompt is sent to `ChatService` with a synthetic session ID (`auto-<taskId>-<epoch>`). After execution, recurring tasks get their `nextRunAt` advanced; one-shot tasks are disabled.

`TaskStore` persists to YAML using atomic temp-file writes. `TaskController` exposes full CRUD at `/tasks` and `/tasks/{id}`.

`PromptCatalog` discovers `.md` files in `./tasks/` and reads YAML frontmatter (via `PromptDocument`) for a `summary` metadata field.

### Tool System (`org.daneel.tool`)

`DaneelToolInterface` is the contract for all tools: name, description, `List<ToolProperty>`, and an `execute(Map<String,Object>)` method. `ToolRegistrar` is a `@Component` that collects all `DaneelToolInterface` beans at startup and generates a JSON schema from their `ToolProperty` metadata. It exposes three methods: `getCallbacks()` (all tools), `getCallbacks(Collection<String> names)` (filtered subset by name), and `catalog()` (ordered `name → description` map used by the tool selector).

`ToolSelector` is a `@Component` that runs before each user turn to build the per-request tool list. It calls `toolSelectorChatClient` with a system prompt containing the full tool catalog (one `- name: description` line per tool) and a transcript of the last `history-window` messages, asking the model to return a JSON array of relevant tool names. The result is parsed (with a substring-scan fallback), filtered to known names, and the matching `ToolCallback[]` is bound to the main chat call via `.toolCallbacks(...)`. An empty response produces a tool-free turn. On any exception, `ToolSelector` falls back to all tools so requests never fail due to the selection step. Every selection is logged as `tool_select count=.. names=..`.

Implemented tools: `TaskCreateTool`, `TaskUpdateTool`, `TaskDeleteTool`, `TaskListTool`, `TaskGetTool`, `PromptListTool`, `PromptCreateTool`, `TimeProviderTool`, `LocalTimezoneTool`, `SpeakTool` (macOS `say` command), `SpawnPerItemTool` (fan-out: spawns one sub-run per item, blocks until all finish or timeout, returns a summary; if it times out returns a `batch_id` to poll), `FanOutStatusTool` (name `fanout_status`: check progress of a timed-out batch by `batch_id`), `FileReadTool`, `FileWriteTool`, `FileDeleteTool`, `FileMoveTool`, `FilePropertiesTool`, `DirectoryCreateTool`, `DirectoryListTool` (sandboxed filesystem access — all paths relative to the configured root, traversal outside root is rejected). `CsvHeadersTool`, `CsvReadTool`, `CsvCreateTool`, `CsvAppendTool`, `CsvFilterCopyTool` (sandboxed CSV access — all under the same `daneel.tools.files.root` sandbox; `csv_headers` returns column names as a JSON array, `csv_read` returns rows as a `{rowIndex: {col: val}}` JSON object, `csv_create` creates a new CSV with headers and optional rows, `csv_append` appends a row matching column order, `csv_filter_copy` copies selected columns with optional rename and row limit). `SeoSearchTool`, `SeoFetchArticleTool`, `SeoSearchAndFetchTool` (DataForSEO integration — `seo_search` calls Google News Live Advanced and returns a JSON array of articles with `result_id`, title, url, domain, and published; `seo_fetch_article` fetches article content via DataForSEO's content parsing API and saves it as a sandboxed `.md` file with YAML frontmatter, accepts an optional `directory` param to save into a subdirectory; `seo_search_and_fetch` combines both — searches and saves all results in one call, requires `keyword` and `directory`; credentials required via `DATAFORSEO_USER` and `DATAFORSEO_KEY` env vars). `YetiForceCrmClient`-backed CRM tools (package `org.daneel.tool.yetiforce`): `yetiforce_lead_list`, `yetiforce_lead_get`, `yetiforce_lead_create`, `yetiforce_lead_update`, `yetiforce_lead_delete`, `yetiforce_account_list`, `yetiforce_account_get`, `yetiforce_account_create`, `yetiforce_account_update`, `yetiforce_account_delete`, `yetiforce_contact_list`, `yetiforce_contact_get`, `yetiforce_contact_create`, `yetiforce_contact_update`, `yetiforce_contact_delete`, `yetiforce_sales_process_list`, `yetiforce_sales_process_get`, `yetiforce_sales_process_create`, `yetiforce_sales_process_update`, `yetiforce_sales_process_delete`. `YetiForceFieldsConfig` loads `src/main/resources/yetiforce-fields.yml` at startup to provide dynamic field definitions for create/update tools. Auth is two-layer: static API key for login, cached session token for all CRUD calls with automatic 401 retry.

`ErrorStore` is a shared `@Component` (in `org.daneel.tool.error`) that collects tool failures across all sessions. `ToolRegistrar.call()` wraps every `tool.execute()` in a try/catch: on exception it logs, calls `errorStore.record(toolName, message)`, and returns `"Error: ..."` to the LLM instead of propagating. `ErrorStore` holds errors in a `ConcurrentLinkedQueue<ToolError>` (capped at 500); `drain()` returns and clears all current entries (each error delivered once); `sweepExpired()` runs on a scheduled interval and removes entries older than `ttl-ms`. `ErrorController` exposes `GET /errors` which drains the store and returns the list as JSON. The frontend polls this endpoint every 5 s and renders each error as a red `.error` bubble.

`SandboxFileSystem` is a shared `@Component` helper (not a tool) that holds the absolute root path and enforces containment: `resolve(userPath)` normalizes the candidate and throws `SandboxAccessException` if it escapes the root. All filesystem tools inject it. `CsvSupport` is a shared `@Component` helper (not a tool) that owns the `CsvMapper` and provides typed `readHeaders`, `readRows`, `write`, `parseRecord`, and `appendRecord` methods; all CSV tools inject it.

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
- `daneel.tools.files.root` — sandbox root directory for filesystem tools (`./rootdir` by default; auto-created on startup)
- `daneel.tools.spawn.block-timeout-ms` — how long `spawn_per_item` blocks waiting for all sub-runs before returning a poll message (default 30000)
- `daneel.tools.dataforseo.api.user` — set via `DATAFORSEO_USER` env var; required for SEO tools
- `daneel.tools.dataforseo.api.key` — set via `DATAFORSEO_KEY` env var; required for SEO tools
- `daneel.tools.yetiforce.url` — set via `YETIFORCE_URL` env var; base URL of your YetiForce instance
- `daneel.tools.yetiforce.api-key` — set via `YETIFORCE_API_KEY` env var; the X-API-KEY from Integration → Web service - Applications
- `daneel.tools.yetiforce.user` — set via `YETIFORCE_USER` env var; WebserviceStandard username
- `daneel.tools.yetiforce.password` — set via `YETIFORCE_PASSWORD` env var; WebserviceStandard password
- `src/main/resources/yetiforce-fields.yml` — field definitions per module (Leads, Accounts, Contacts, SSalesProcesses); edit to match your YetiForce instance's actual field names
- `daneel.tools.select.enabled` — run a dedicated SLM call before each user turn to select relevant tools (default `true`; set to `false` to revert to sending all tools on every request)
- `daneel.tools.select.history-window` — number of recent messages passed to the tool selector for context (default `4`)
- `daneel.tools.errors.ttl-ms` — how long tool errors are kept in the store before TTL sweep removes them (default 300000 = 5 min)
- `daneel.tools.errors.sweep-interval-ms` — how often the TTL sweep runs (default 60000 = 1 min)
- `daneel.llm.serialize-calls` — serialize all LMStudio HTTP calls through a single-permit fair semaphore so only one inference runs at a time (default `true`; prevents concurrent-request failures during fan-out; set to `false` for backends that support concurrent inference)

`src/main/resources/system-prompt.md` — the LLM system prompt (currently French-language, concise/friendly persona).

## Testing Patterns

- `ChatServiceTest` mocks `ChatClient` with `Answers.RETURNS_DEEP_STUBS` and uses low thresholds (200 chars, keep 2) to exercise compaction logic without huge histories. Also mocks `ToolSelector` (default: returns empty array).
- `ToolSelectorTest` mocks `toolSelectorChatClient` with `RETURNS_DEEP_STUBS` and uses a real `ToolRegistrar` with stub tools; covers JSON parse, substring fallback, empty response, exception fallback, and disabled mode.
- `ToolRegistrarTest` covers `catalog()` and the filtered `getCallbacks(names)` overload.
- `TaskStoreTest` uses JUnit 5 `@TempDir` for isolated file I/O.
- Tool tests mock `TaskStore` / `PromptCatalog` and verify with `ArgumentCaptor`.
- `PromptCreateToolTest` is known-flaky when run in suite (timing-sensitive filename generation); passes reliably in isolation.

## Apache Camel

Three Camel routes are active:
- `TaskPollRoute` (`task-poll`) — timer-driven, splits due tasks, calls `TaskPoller.run` per task.
- `FanOutRoute` (`fan-out`) — `seda:fanout` consumer (1 worker), calls `FanOutRunner.run` per item enqueued by `SpawnPerItemTool`. `FanOutRunner` catches all exceptions internally and records success/failure to `FanOutTracker`; the route's `onException` handler is a backstop only.
- `TelegramRoute` (`telegram-inbound`) — long-polls Telegram for text messages; only registered when `daneel.telegram.enabled=true`. Filters by allowlist, delegates to `TelegramRunner` which calls `ChatService.chat("tg-<chatId>", text)`, and sends the reply back (chunked to ≤4096 chars per Telegram's limit).
