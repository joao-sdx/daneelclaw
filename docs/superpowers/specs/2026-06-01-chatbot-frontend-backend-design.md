# Chatbot — Frontend + Backend Design

**Date:** 2026-06-01
**Status:** Approved

## Context

The project already has a Spring Boot 3.5.14 + Camel 4.14.7 scaffold with a stub `POST /chat`
endpoint. This spec covers implementing the chatbot end-to-end: connecting to a local LMStudio
instance as the LLM provider and adding a browser-based chat UI served by Spring Boot.

## Decisions

| Concern | Decision |
|---|---|
| Frontend | Static HTML/JS in `src/main/resources/static/` — served by Spring Boot, no build tool |
| LLM client | Spring AI OpenAI starter pointed at LMStudio's OpenAI-compatible API |
| Conversation history | Server-side, in-memory per session (`ConcurrentHashMap<sessionId, List<Message>>`) |
| UI theme | Light (white background, dark text) |
| Persistence | None — history lost on restart |

## Architecture

```
Browser (index.html + chat.js)
    │  POST /chat  {"sessionId": "...", "message": "..."}
    ▼
ChatController
    │
    ▼
ChatService  ──── history: ConcurrentHashMap<String, List<Message>>
    │
    ▼
Spring AI ChatClient
    │  OpenAI-compatible REST
    ▼
LMStudio  (default: http://localhost:1234)
```

Session ID is generated in the browser using `crypto.randomUUID()` on page load and sent with
every request. The server uses it to look up or initialise the conversation history.

## Backend

### Dependencies

Add to `pom.xml`:
- `spring-ai-bom` version `1.0.0` in `<dependencyManagement>` (BOM import)
- `spring-ai-openai-spring-boot-starter` in `<dependencies>` (no version — from BOM)

### Configuration (`application.yml`)

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:1234
      api-key: not-needed
      chat:
        options:
          model: ${LMSTUDIO_MODEL:local-model}
```

`base-url` points at LMStudio. `api-key` is required by Spring AI but ignored by LMStudio —
set to a placeholder. Model name is overridable via the `LMSTUDIO_MODEL` environment variable;
defaults to `local-model` (LMStudio selects the currently loaded model when the name is
unrecognised, so this works out of the box).

### `ChatRequest` record

Add `sessionId` field:

```java
public record ChatRequest(String sessionId, String message) {}
```

### `ChatService`

New class, `org.daneel.chat.ChatService`:
- Annotated `@Service`
- Injects `ChatClient` (auto-configured by Spring AI)
- Field: `Map<String, List<Message>> history = new ConcurrentHashMap<>()`
- Method: `String chat(String sessionId, String userMessage)`
  1. `history.computeIfAbsent(sessionId, k -> new ArrayList<>())`
  2. Add `new UserMessage(userMessage)` to the list
  3. Call `chatClient.prompt().messages(messages).call().content()`
  4. Add `new AssistantMessage(reply)` to the list
  5. Return `reply`

### `ChatController`

Updated to inject `ChatService` (constructor injection) and delegate:

```java
@PostMapping("/chat")
public ChatResponse chat(@RequestBody ChatRequest request) {
    var reply = chatService.chat(request.sessionId(), request.message());
    return new ChatResponse(reply);
}
```

## Frontend

### `src/main/resources/static/index.html`

Full-page layout:
- `<header>` with app title "Daneelclaw"
- `<div id="messages">` — scrollable message list, takes remaining vertical space
- `<footer>` — input bar: `<textarea>` (single-line feel) + Send `<button>`

CSS: plain, no framework. Light theme (white `#ffffff` background, `#1a1a1a` text).
Message bubbles:
- User: right-aligned, `#0066cc` background, white text
- Assistant: left-aligned, `#f0f0f0` background, dark text

### `src/main/resources/static/chat.js`

On page load:
- `const sessionId = crypto.randomUUID()`

Send logic (triggered by button click or Enter key, Shift+Enter for newline):
1. Read and trim input value; ignore if empty
2. Append user bubble to `#messages`
3. Clear input, disable send button
4. `POST /chat` with `{sessionId, message}`, `Content-Type: application/json`
5. On response: append assistant bubble, re-enable send, scroll to bottom
6. On error: append error bubble, re-enable send

## Error handling

- If the LMStudio server is unreachable, Spring AI throws an exception. `ChatController` does not
  catch it — Spring Boot's default error handler returns a 500. The frontend detects non-2xx
  responses and shows an inline error message ("Something went wrong. Is LMStudio running?").
- No retry logic.

## Verification

1. Start LMStudio and load a model.
2. `./start.sh` — app starts on port 8080.
3. Open `http://localhost:8080` in a browser — chat UI loads.
4. Send a message — assistant responds with text from LMStudio.
5. Send a follow-up that references the first exchange — model demonstrates it has context (confirms
   history is working).
6. Stop and restart the app — history is gone (confirms no persistence, as designed).

## Out of scope

- Streaming responses (SSE) — can be added later
- Persistent history (database)
- Authentication
- Camel routes (separate future step)
- Multiple simultaneous users at scale (in-memory map has no eviction)
