# Chatbot Frontend + Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing Spring Boot scaffold to LMStudio via Spring AI, add per-session in-memory conversation history, and serve a light-theme chat UI from `src/main/resources/static/`.

**Architecture:** `ChatService` owns LLM interaction and a `ConcurrentHashMap<sessionId, List<Message>>` for per-session history. `ChatController` delegates to it. A static `index.html` + `chat.js` is served by Spring Boot's built-in static handler; the browser generates a UUID session ID and sends it with every request.

**Tech Stack:** Spring Boot 3.5.14, Spring AI 1.0.0 (OpenAI-compatible client pointed at LMStudio localhost:1234), Lombok, JUnit 5 + Mockito (via spring-boot-starter-test), AssertJ, vanilla HTML/CSS/JS.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `pom.xml` | Add Spring AI BOM, OpenAI starter, test starter |
| Modify | `src/main/resources/application.yml` | Spring AI config (base-url, api-key, model) |
| Modify | `src/main/java/org/daneel/chat/ChatRequest.java` | Add `sessionId` field |
| Create | `src/main/java/org/daneel/chat/ChatService.java` | LLM calls + session history map |
| Modify | `src/main/java/org/daneel/chat/ChatController.java` | Delegate to ChatService |
| Create | `src/main/resources/static/index.html` | Full-page chat UI |
| Create | `src/main/resources/static/chat.js` | Send/receive logic |
| Create | `src/test/java/org/daneel/chat/ChatServiceTest.java` | Unit tests for service |
| Create | `src/test/java/org/daneel/chat/ChatControllerTest.java` | Slice test for controller |

---

## Task 1: Add Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add Spring AI BOM to `<dependencyManagement>`**

  Insert after the `camel-spring-boot-bom` import in the existing `<dependencyManagement>` block:

  ```xml
  <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
  </dependency>
  ```

- [ ] **Step 2: Add runtime + test dependencies**

  Insert after the `camel-spring-boot-starter` dependency:

  ```xml
  <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
  </dependency>
  ```

- [ ] **Step 3: Verify compilation**

  ```bash
  ./mvnw compile -q
  ```

  Expected: no errors (Unsafe warnings from Lombok are fine).

- [ ] **Step 4: Commit**

  ```bash
  git add pom.xml
  git commit -m "feat: add spring-ai and test dependencies"
  ```

---

## Task 2: Configure Spring AI

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add Spring AI config**

  Replace the entire file with:

  ```yaml
  spring:
    application:
      name: daneelclaw
    ai:
      openai:
        base-url: http://localhost:1234
        api-key: not-needed
        chat:
          options:
            model: ${LMSTUDIO_MODEL:local-model}

  server:
    port: 8080
  ```

  `api-key` is required by Spring AI's auto-configuration but ignored by LMStudio. `LMSTUDIO_MODEL` can be overridden via env var; the default `local-model` causes LMStudio to use whichever model is currently loaded.

- [ ] **Step 2: Verify the app starts**

  ```bash
  ./mvnw spring-boot:run &
  sleep 12
  curl -s localhost:8080/chat -X POST \
    -H 'Content-Type: application/json' \
    -d '{"sessionId":"test","message":"hi"}' | head -c 80
  kill %1
  ```

  Expected: the echo stub still responds with `{"message":"hi"}` (ChatService not wired yet).

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/resources/application.yml
  git commit -m "feat: configure spring-ai for lmstudio"
  ```

---

## Task 3: Update ChatRequest

**Files:**
- Modify: `src/main/java/org/daneel/chat/ChatRequest.java`

- [ ] **Step 1: Add `sessionId` field**

  Replace the file content:

  ```java
  package org.daneel.chat;

  public record ChatRequest(String sessionId, String message) {}
  ```

- [ ] **Step 2: Verify compilation**

  ```bash
  ./mvnw compile -q
  ```

  Expected: success.

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/java/org/daneel/chat/ChatRequest.java
  git commit -m "feat: add sessionId to ChatRequest"
  ```

---

## Task 4: Implement ChatService (TDD)

**Files:**
- Create: `src/test/java/org/daneel/chat/ChatServiceTest.java`
- Create: `src/main/java/org/daneel/chat/ChatService.java`

- [ ] **Step 1: Create test directory structure**

  ```bash
  mkdir -p src/test/java/org/daneel/chat
  ```

- [ ] **Step 2: Write the failing tests**

  Create `src/test/java/org/daneel/chat/ChatServiceTest.java`:

  ```java
  package org.daneel.chat;

  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.Answers;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import org.springframework.ai.chat.client.ChatClient;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.anyList;
  import static org.mockito.Mockito.lenient;
  import static org.mockito.Mockito.times;
  import static org.mockito.Mockito.verify;

  @ExtendWith(MockitoExtension.class)
  class ChatServiceTest {

      @Mock(answer = Answers.RETURNS_DEEP_STUBS)
      private ChatClient chatClient;

      private ChatService chatService;

      @BeforeEach
      void setUp() {
          chatService = new ChatService(chatClient);
          lenient().when(chatClient.prompt().messages(anyList()).call().content())
                   .thenReturn("AI reply");
      }

      @Test
      void chat_returnsReply() {
          assertThat(chatService.chat("s1", "hello")).isEqualTo("AI reply");
      }

      @Test
      void chat_callsLlmForEachMessage() {
          chatService.chat("s1", "first");
          chatService.chat("s1", "second");
          verify(chatClient.prompt().messages(anyList()).call(), times(2)).content();
      }

      @Test
      void chat_isolatesSessionHistory() {
          chatService.chat("session-a", "hello");
          var reply = chatService.chat("session-b", "hello");
          assertThat(reply).isEqualTo("AI reply");
      }
  }
  ```

- [ ] **Step 3: Run tests to confirm they fail**

  ```bash
  ./mvnw test -pl . -Dtest=ChatServiceTest -q 2>&1 | tail -10
  ```

  Expected: compilation error — `ChatService` not found.

- [ ] **Step 4: Create `ChatService.java`**

  Create `src/main/java/org/daneel/chat/ChatService.java`:

  ```java
  package org.daneel.chat;

  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.ai.chat.messages.AssistantMessage;
  import org.springframework.ai.chat.messages.Message;
  import org.springframework.ai.chat.messages.UserMessage;
  import org.springframework.stereotype.Service;

  import java.util.ArrayList;
  import java.util.List;
  import java.util.Map;
  import java.util.concurrent.ConcurrentHashMap;

  @Slf4j
  @Service
  @RequiredArgsConstructor
  public class ChatService {

      private final ChatClient chatClient;
      private final Map<String, List<Message>> history = new ConcurrentHashMap<>();

      public String chat(String sessionId, String userMessage) {
          var messages = history.computeIfAbsent(sessionId, k -> new ArrayList<>());
          messages.add(new UserMessage(userMessage));
          log.info("chat_service sessionId={} historySize={}", sessionId, messages.size());
          var reply = chatClient.prompt()
                  .messages(messages)
                  .call()
                  .content();
          messages.add(new AssistantMessage(reply));
          return reply;
      }
  }
  ```

- [ ] **Step 5: Run tests to confirm they pass**

  ```bash
  ./mvnw test -pl . -Dtest=ChatServiceTest -q 2>&1 | tail -10
  ```

  Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/org/daneel/chat/ChatService.java \
          src/test/java/org/daneel/chat/ChatServiceTest.java
  git commit -m "feat: implement ChatService with in-memory session history"
  ```

---

## Task 5: Update ChatController (TDD)

**Files:**
- Create: `src/test/java/org/daneel/chat/ChatControllerTest.java`
- Modify: `src/main/java/org/daneel/chat/ChatController.java`

- [ ] **Step 1: Write the failing controller test**

  Create `src/test/java/org/daneel/chat/ChatControllerTest.java`:

  ```java
  package org.daneel.chat;

  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.http.MediaType;
  import org.springframework.test.context.bean.override.mockito.MockitoBean;
  import org.springframework.test.web.servlet.MockMvc;

  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  @WebMvcTest(ChatController.class)
  class ChatControllerTest {

      @Autowired
      private MockMvc mockMvc;

      @MockitoBean
      private ChatService chatService;

      @Test
      void postChat_returnsServiceReply() throws Exception {
          when(chatService.chat("s1", "hello")).thenReturn("Hi there");

          mockMvc.perform(post("/chat")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"sessionId\":\"s1\",\"message\":\"hello\"}"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.message").value("Hi there"));
      }

      @Test
      void postChat_returns200ForEmptyMessage() throws Exception {
          when(chatService.chat("s1", "")).thenReturn("Please say something");

          mockMvc.perform(post("/chat")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"sessionId\":\"s1\",\"message\":\"\"}"))
                  .andExpect(status().isOk());
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -pl . -Dtest=ChatControllerTest -q 2>&1 | tail -15
  ```

  Expected: test runs but `postChat_returnsServiceReply` fails — controller returns the echo, not the service reply.

- [ ] **Step 3: Update `ChatController.java`**

  Replace the file:

  ```java
  package org.daneel.chat;

  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.web.bind.annotation.PostMapping;
  import org.springframework.web.bind.annotation.RequestBody;
  import org.springframework.web.bind.annotation.RestController;

  @Slf4j
  @RestController
  @RequiredArgsConstructor
  public class ChatController {

      private final ChatService chatService;

      @PostMapping("/chat")
      public ChatResponse chat(@RequestBody ChatRequest request) {
          log.info("chat_request sessionId={}", request.sessionId());
          var reply = chatService.chat(request.sessionId(), request.message());
          return new ChatResponse(reply);
      }
  }
  ```

- [ ] **Step 4: Run all tests**

  ```bash
  ./mvnw test -q 2>&1 | tail -10
  ```

  Expected: `Tests run: 5, Failures: 0, Errors: 0` (3 service tests + 2 controller tests).

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/org/daneel/chat/ChatController.java \
          src/test/java/org/daneel/chat/ChatControllerTest.java
  git commit -m "feat: wire ChatController to ChatService"
  ```

---

## Task 6: Create Chat Frontend

**Files:**
- Create: `src/main/resources/static/index.html`
- Create: `src/main/resources/static/chat.js`

- [ ] **Step 1: Create `index.html`**

  Create `src/main/resources/static/index.html`:

  ```html
  <!DOCTYPE html>
  <html lang="en">
  <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Daneelclaw</title>
      <style>
          * { box-sizing: border-box; margin: 0; padding: 0; }
          body { font-family: system-ui, sans-serif; background: #fff; color: #1a1a1a; height: 100vh; display: flex; flex-direction: column; }
          header { padding: 1rem 1.5rem; border-bottom: 1px solid #e0e0e0; font-weight: 600; font-size: 1.1rem; }
          #messages { flex: 1; overflow-y: auto; padding: 1rem 1.5rem; display: flex; flex-direction: column; gap: 0.75rem; }
          .bubble { max-width: 70%; padding: 0.6rem 0.9rem; border-radius: 12px; line-height: 1.5; word-wrap: break-word; white-space: pre-wrap; }
          .user { align-self: flex-end; background: #0066cc; color: #fff; border-bottom-right-radius: 4px; }
          .assistant { align-self: flex-start; background: #f0f0f0; color: #1a1a1a; border-bottom-left-radius: 4px; }
          .error { align-self: flex-start; background: #fff0f0; color: #cc0000; border-bottom-left-radius: 4px; }
          footer { display: flex; gap: 0.5rem; padding: 1rem 1.5rem; border-top: 1px solid #e0e0e0; }
          #input { flex: 1; padding: 0.6rem 0.9rem; border: 1px solid #ccc; border-radius: 8px; font-size: 1rem; resize: none; font-family: inherit; }
          #input:focus { outline: none; border-color: #0066cc; }
          #send { padding: 0.6rem 1.2rem; background: #0066cc; color: #fff; border: none; border-radius: 8px; font-size: 1rem; cursor: pointer; }
          #send:disabled { background: #aaa; cursor: not-allowed; }
          #send:hover:not(:disabled) { background: #0052a3; }
      </style>
  </head>
  <body>
      <header>Daneelclaw</header>
      <div id="messages"></div>
      <footer>
          <textarea id="input" placeholder="Type a message…" rows="2"></textarea>
          <button id="send">Send</button>
      </footer>
      <script src="chat.js"></script>
  </body>
  </html>
  ```

- [ ] **Step 2: Create `chat.js`**

  Create `src/main/resources/static/chat.js`:

  ```javascript
  const sessionId = crypto.randomUUID();
  const messagesEl = document.getElementById('messages');
  const inputEl = document.getElementById('input');
  const sendEl = document.getElementById('send');

  function addBubble(text, role) {
      const div = document.createElement('div');
      div.className = `bubble ${role}`;
      div.textContent = text;
      messagesEl.appendChild(div);
      messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  async function sendMessage() {
      const text = inputEl.value.trim();
      if (!text) return;

      addBubble(text, 'user');
      inputEl.value = '';
      sendEl.disabled = true;
      inputEl.disabled = true;

      try {
          const response = await fetch('/chat', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ sessionId, message: text })
          });
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          const data = await response.json();
          addBubble(data.message, 'assistant');
      } catch {
          addBubble('Something went wrong. Is LMStudio running?', 'error');
      } finally {
          sendEl.disabled = false;
          inputEl.disabled = false;
          inputEl.focus();
      }
  }

  sendEl.addEventListener('click', sendMessage);
  inputEl.addEventListener('keydown', e => {
      if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          sendMessage();
      }
  });
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/resources/static/index.html \
          src/main/resources/static/chat.js
  git commit -m "feat: add static chat frontend"
  ```

---

## Task 7: End-to-End Smoke Test

- [ ] **Step 1: Start LMStudio and load a model**

  Open LMStudio, load a model, and start the local server on port 1234 (default).

- [ ] **Step 2: Start the app**

  ```bash
  ./start.sh
  ```

  Expected in logs:
  - `Spring Boot :: (v3.5.14)` — Spring started
  - `Apache Camel 4.14.7 ... started` — Camel started with 0 routes
  - `Tomcat started on port 8080`

- [ ] **Step 3: Verify the UI loads**

  Open `http://localhost:8080` in a browser. Expected: light-theme chat interface with header "Daneelclaw", message area, and input at the bottom.

- [ ] **Step 4: Send a first message**

  Type "What is 2 + 2?" and press Enter. Expected: user bubble appears immediately, then an assistant bubble with the model's reply.

- [ ] **Step 5: Verify history is working**

  Send a follow-up like "What did I just ask?" Expected: the model's reply references the first question, confirming conversation history is sent.

- [ ] **Step 6: Verify curl still works**

  ```bash
  curl -s -X POST localhost:8080/chat \
    -H 'Content-Type: application/json' \
    -d '{"sessionId":"cli-test","message":"Say hello"}'
  ```

  Expected: `{"message":"Hello! ..."}` (or similar LLM reply).

- [ ] **Step 7: Verify history is session-scoped**

  Stop (`Ctrl+C`) and restart `./start.sh`. Send a message. Expected: model has no memory of the previous session (confirms no persistence).
