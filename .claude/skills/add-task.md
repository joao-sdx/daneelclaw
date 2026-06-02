---
name: add-task
description: Use when scheduling a new automated task in DaneelClaw — one-shot or recurring. Triggers on requests like "schedule a task", "add a recurring task", "run a prompt at X time", "create a cron-style task".
---

# add-task

## Overview

DaneelClaw's task scheduler reads `./tasks/tasks.yml` at startup and polls it every minute.
Each task has a prompt file (Markdown) stored in the same `./tasks/` directory.
`TaskPoller` fires tasks whose `nextRunAt` is in the past, feeds the prompt to `ChatService`,
and reschedules recurring tasks.

Key files:
- Task list: `tasks/tasks.yml`
- Prompt files: `tasks/*.md`
- Task model: `src/main/java/org/daneel/task/PlannedTask.java`

## Steps

1. **Clarify the task** — ask for:
   - What should happen (the prompt / action)
   - When it should run (date + time + timezone)
   - One-shot or recurring? If recurring, how often (in minutes)?
2. **Convert the time to UTC** — `nextRunAt` must be an ISO-8601 instant with `Z` suffix.
   Example: "17h00 Paris CEST (UTC+2)" → `2026-06-01T15:00:00Z`
3. **Write the prompt file** — create `tasks/<id>.md` with a clear instruction for the LLM.
   Use the speak tool for audio output if the task requires sound.
   Available placeholders: `{trigger_time_gmt}`, `{current_time_gmt}`,
   `{trigger_time_local}`, `{current_time_local}`
4. **Add the task entry** to `tasks/tasks.yml`:
   ```yaml
   - id: "<kebab-case-id>"
     name: "<human-readable name>"
     promptFile: "<id>.md"
     nextRunAt: "<UTC ISO-8601>"
     recurringIntervalMinutes: <minutes or null>
     enabled: true
   ```
5. **Verify** — read back the tasks.yml entry to confirm it parses correctly.
   If the app is running, `GET /tasks` should return the new task.

## Guardrails

- `nextRunAt` is always UTC — convert local times explicitly, never guess
- `promptFile` path is relative to the tasks directory, not the project root
- `recurringIntervalMinutes: null` for one-shot tasks
- Task `id` must be unique in tasks.yml
- After a one-shot task fires, `enabled` is set to `false` automatically — no action needed