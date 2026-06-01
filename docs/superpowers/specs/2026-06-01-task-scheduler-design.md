# Planned & Recurring Task Scheduler Design

**Date:** 2026-06-01
**Status:** Approved

## Context

DaneelClaw currently only responds to user-initiated chat requests. This spec adds a task scheduler
that lets operators define planned tasks — each with a prompt file, a scheduled run time (GMT), and
an optional recurrence interval. At the scheduled time the system starts an autonomous LLM session
(no user present), executes whatever tool calls the model makes, and stops when the model returns no
further tool calls. Recurring tasks reschedule themselves to the next future slot after each run,
skipping over any missed periods (server-down safety).

## Package

All new code lives in `org.daneel.task`. Existing code in `org.daneel.chat` and `org.daneel.tool`
is unchanged except:
- `DaneelclawApplication` gains `@EnableScheduling`
- `pom.xml` gains `jackson-dataformat-yaml`
- `application.yml` gains `daneel.scheduler.*` properties

## File Map

| Action | Path |
|--------|------|
| Create | `src/main/java/org/daneel/task/PlannedTask.java` |
| Create | `src/main/java/org/daneel/task/TaskRequest.java` |
| Create | `src/main/java/org/daneel/task/TaskStore.java` |
| Create | `src/main/java/org/daneel/task/PromptResolver.java` |
| Create | `src/main/java/org/daneel/task/TaskPoller.java` |
| Create | `src/main/java/org/daneel/task/TaskController.java` |
| Modify | `src/main/java/org/daneel/DaneelclawApplication.java` |
| Modify | `pom.xml` |
| Modify | `src/main/resources/application.yml` |
| Create | `src/test/java/org/daneel/task/TaskStoreTest.java` |
| Create | `src/test/java/org/daneel/task/PromptResolverTest.java` |
| Create | `src/test/java/org/daneel/task/TaskPollerTest.java` |
| Create | `src/test/java/org/daneel/task/TaskControllerTest.java` |

## Data Model

### `PlannedTask` record

```java
package org.daneel.task;

import java.time.Instant;

public record PlannedTask(
        String id,
        String name,
        String promptFile,
        Instant nextRunAt,
        Integer recurringIntervalMinutes,
        boolean enabled
) {}
```

- `nextRunAt` — always UTC, stored/transmitted as ISO-8601 (e.g. `"2026-06-01T14:00:00Z"`).
- `recurringIntervalMinutes` — `null` means one-shot.
- `promptFile` — filename relative to the tasks directory (e.g. `"task-1.md"`).

### `TaskRequest` record (create/update body)

```java
public record TaskRequest(
        @NotBlank String name,
        @NotBlank String promptFile,
        @NotNull Instant nextRunAt,
        Integer recurringIntervalMinutes,
        boolean enabled
) {}
```

## File Layout on Disk

Governed by one property: `daneel.scheduler.tasks-dir` (default `./tasks`).

```
./tasks/
  tasks.yml       ← the task registry
  task-1.md       ← prompt for task-1
  task-2.md       ← prompt for task-2
  ...
```

### `tasks.yml` format

```yaml
tasks:
  - id: "task-1"
    name: "Tunisia time announcement"
    promptFile: "task-1.md"
    nextRunAt: "2026-06-01T14:00:00Z"
    recurringIntervalMinutes: 60
    enabled: true
```

If `tasks.yml` does not exist the system starts with an empty task list and creates the file on
first write. The `./tasks` directory is created automatically if absent.

## Prompt Files

Each `.md` file in the tasks directory is a free-form prompt. It may contain any of these
placeholders, which are resolved at the moment the task fires:

| Placeholder | Value |
|---|---|
| `{trigger_time_gmt}` | `task.nextRunAt` formatted as ISO-8601 UTC |
| `{current_time_gmt}` | Actual `Instant.now()` at execution, ISO-8601 UTC |
| `{trigger_time_local}` | `task.nextRunAt` converted to system default timezone |
| `{current_time_local}` | Actual `Instant.now()` converted to system default timezone |

Example `./tasks/task-1.md`:
```
Tell in audio what time is it now in Tunisia.
This run was scheduled for {trigger_time_gmt} (GMT) / {trigger_time_local} (local).
It is actually running at {current_time_gmt} (GMT) / {current_time_local} (local).
```

## `TaskStore`

`@Component`. Reads/writes `{tasksDir}/tasks.yml` using `ObjectMapper` with the YAML module. All
public methods are `synchronized` to prevent concurrent read/write conflicts between `TaskPoller`
and `TaskController`.

```
findAll()                → List<PlannedTask>
save(PlannedTask task)   → void  (adds if not present, replaces if ID matches)
delete(String id)        → void
findById(String id)      → Optional<PlannedTask>
```

File writes use a write-then-rename strategy to avoid partial writes (write to
`tasks.yml.tmp`, then `Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE)`).

The `ObjectMapper` is configured with `JavaTimeModule` (Instants serialized as ISO-8601 strings,
not epoch millis) and `WRITE_DATES_AS_TIMESTAMPS = false`.

## `PromptResolver`

`@Component`. Stateless — reads the prompt file each time it is called (picks up edits without
restart).

```
resolve(PlannedTask task, Instant triggerTime, Instant currentTime) → String
```

Reads `{tasksDir}/{task.promptFile()}`, performs the four placeholder replacements, returns the
resolved string. Throws `IOException` (wrapped in `UncheckedIOException`) if the file is missing —
callers log and skip the task for this run.

Local timezone formatting uses `ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())` with
`DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")`.

## `TaskPoller`

`@Component`. Uses Spring's `@Scheduled`. Added `@EnableScheduling` to `DaneelclawApplication`.

```java
@Scheduled(fixedRateString = "${daneel.scheduler.check-interval-ms:60000}")
void poll() {
    var now = Instant.now();
    taskStore.findAll().stream()
            .filter(PlannedTask::enabled)
            .filter(task -> !now.isBefore(task.nextRunAt()))
            .forEach(task -> trigger(task, now));
}
```

**Trigger sequence:**

```java
void trigger(PlannedTask task, Instant triggerTime) {
    try {
        var currentTime = Instant.now();
        var prompt = promptResolver.resolve(task, triggerTime, currentTime);
        var sessionId = "auto-" + task.id() + "-" + triggerTime.toEpochMilli();
        chatService.chat(sessionId, prompt);
        log.info("task_completed id={} name={}", task.id(), task.name());
    } catch (Exception e) {
        log.error("task_execution_failed id={} name={}", task.id(), task.name(), e);
    } finally {
        reschedule(task, triggerTime);
    }
}
```

Always reschedules (in `finally`) so a failed run doesn't block future executions.

**Rescheduling (server-down safety):**

```java
void reschedule(PlannedTask task, Instant triggerTime) {
    if (task.recurringIntervalMinutes() == null || task.recurringIntervalMinutes() <= 0) {
        // one-shot: disable after run
        taskStore.save(new PlannedTask(task.id(), task.name(), task.promptFile(),
                task.nextRunAt(), task.recurringIntervalMinutes(), false));
        return;
    }
    // recurring: advance to next future slot (skip missed periods)
    var now = Instant.now();
    var next = task.nextRunAt().plus(task.recurringIntervalMinutes(), ChronoUnit.MINUTES);
    while (!next.isAfter(now)) {
        next = next.plus(task.recurringIntervalMinutes(), ChronoUnit.MINUTES);
    }
    taskStore.save(new PlannedTask(task.id(), task.name(), task.promptFile(),
            next, task.recurringIntervalMinutes(), task.enabled()));
}
```

`while (!next.isAfter(now))` loop advances `nextRunAt` until it is strictly in the future, no
matter how many intervals were missed. The task fires exactly once per call to `trigger` regardless.

## `TaskController`

```
POST   /tasks           create  → 201 + PlannedTask (ID generated as UUID)
GET    /tasks           list    → 200 + List<PlannedTask>
GET    /tasks/{id}      get     → 200 + PlannedTask | 404
PUT    /tasks/{id}      update  → 200 + PlannedTask | 404
DELETE /tasks/{id}      delete  → 204 | 404
```

`POST` generates a UUID for `id`. The prompt file named in `promptFile` is not created
automatically — the operator is responsible for placing the `.md` file in the tasks directory before
enabling the task.

## Configuration

```yaml
daneel:
  tools:
    speak:
      command: "say -v Thomas {text}"
  scheduler:
    tasks-dir: "./tasks"          # directory containing tasks.yml and prompt files
    check-interval-ms: 60000     # how often the poller checks (ms)
```

## Error Handling

| Condition | Behaviour |
|---|---|
| `tasks.yml` missing at startup | Empty list; file created on first write |
| `./tasks` directory missing | Created automatically on first write |
| Prompt file missing | Log error, skip execution this run, still reschedule |
| LLM / tool failure | Log error, still reschedule (execution is best-effort) |
| `tasks.yml` write failure | Log error; in-memory state may diverge from disk |
| Task ID not found on PUT/DELETE | Return 404 |

## Testing

- `TaskStoreTest` — write tasks, read back, concurrent safety (call save + findAll from two threads),
  missing-file case, atomic rename.
- `PromptResolverTest` — all four placeholders substituted, file-not-found throws, no-placeholder
  file returned verbatim.
- `TaskPollerTest` — due task is triggered; future task skipped; recurring task advances correctly
  after simulated missed periods; one-shot task is disabled after run; exception in execution still
  reschedules.
- `TaskControllerTest` — `@WebMvcTest` + `@MockitoBean TaskStore`; CRUD happy paths and 404 cases.

## Autonomous Session

No new code needed. `chatService.chat(sessionId, resolvedPrompt)` calls the existing
`ChatService.chat()` method, which passes the prompt to Spring AI's `ChatClient`. Spring AI
automatically handles the tool-call loop: model calls tool → tool executes → result returned to
model → repeat until model response contains no tool calls → return final text. The autonomous
session terminates naturally.

The session ID uses pattern `"auto-{taskId}-{triggerEpochMs}"` to keep it distinct from
user-initiated sessions and make it traceable in logs.

## Verification

1. `./mvnw test` — all existing 24 tests pass plus the new task tests.
2. Create `./tasks/` directory and a `task-1.md` file with placeholders.
3. `POST /tasks` to create a recurring task with `nextRunAt` 1 minute in the future.
4. `GET /tasks` confirms the task is stored.
5. After 1 minute: observe logs — `task_completed id=task-1`; if `SpeakTool` is configured the
   system speaks; `nextRunAt` in `tasks.yml` advances to the next future slot.
6. Stop the app for 3 intervals, restart — task fires once and `nextRunAt` jumps ahead (not back to
   the first missed slot).

## Out of Scope

- Task history / execution log persistence.
- Concurrent task execution guard (if a task takes longer than the check interval).
- Web UI for task management.
- Authentication on `/tasks` endpoints.
