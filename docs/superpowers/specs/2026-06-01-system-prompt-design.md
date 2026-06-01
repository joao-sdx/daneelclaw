# System Prompt Resource Design

**Date:** 2026-06-01
**Status:** Approved

## Context

The chatbot currently sends only the user's conversation history to LMStudio — there is no system
prompt establishing the assistant's identity or behaviour. This adds a markdown resource holding the
system prompt and wires it as the `ChatClient`'s default system message, so every request carries the
assistant's identity ("DaneelClaw, a personal assistant, here to help") without changing per-request
code.

## Decisions

| Concern | Decision |
|---|---|
| Wiring | `ChatClient.Builder.defaultSystem(Resource)` in `ChatConfig` |
| Content | Short & structured: name, role, purpose, a few light behavioural guidelines |
| Storage | Classpath resource `src/main/resources/system-prompt.md` |

## Files

### `src/main/resources/system-prompt.md` (new)

```
You are DaneelClaw, a personal assistant. You are here to help.

- Be concise and clear in your responses.
- Be friendly and professional.
- If a request is unclear, ask for clarification before answering.
```

### `src/main/java/org/daneel/chat/ChatConfig.java` (modify)

Inject the resource and apply it as the default system message when building the `ChatClient`:

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder,
                      @Value("classpath:system-prompt.md") Resource systemPrompt) {
    return builder.defaultSystem(systemPrompt).build();
}
```

Add imports: `org.springframework.beans.factory.annotation.Value`, `org.springframework.core.io.Resource`.

The existing `http11RestClientCustomizer` bean is unchanged.

## Why this approach

- Spring AI 1.0.0's `ChatClient.Builder.defaultSystem(Resource)` reads the resource and applies it as
  a system message on every prompt automatically. `ChatService` keeps calling
  `chatClient.prompt().messages(history).call()`; the default system message is prepended by the
  client.
- Per-session history (`ConcurrentHashMap<sessionId, List<Message>>`) stays free of the system
  message — no duplication, no storage of the prompt per session.
- Editing the prompt is a one-file change with no code edits.

## Error handling

If `system-prompt.md` is missing from the classpath, Spring fails fast at startup when resolving the
`@Value("classpath:system-prompt.md")` resource — a loud, immediate failure rather than a silent
misconfiguration. This is acceptable and desirable.

## Verification

1. `./mvnw test` — existing 5 tests still pass (they mock `ChatClient`; this change only affects bean
   construction, not the mocked unit tests).
2. `./start.sh` with LMStudio running.
3. In the browser, send: "What is your name?" — the assistant identifies as DaneelClaw, confirming
   the system prompt is in effect.
4. Optionally edit `system-prompt.md`, restart, and confirm the behaviour changes — proving the file
   drives the prompt.

## Out of scope

- Per-session or per-user system prompts.
- Templating / variable substitution in the prompt.
- Runtime reloading without restart.
