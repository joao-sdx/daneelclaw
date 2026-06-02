# System Prompt Resource Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `system-prompt.md` classpath resource defining DaneelClaw's identity and wire it as the `ChatClient`'s default system message.

**Architecture:** A markdown file in `src/main/resources/` holds the system prompt. `ChatConfig` injects it as a Spring `Resource` via `@Value("classpath:system-prompt.md")` and applies it with `ChatClient.Builder.defaultSystem(resource)`. The system message is then prepended to every request automatically; `ChatService` and per-session history are untouched.

**Tech Stack:** Spring Boot 3.5.14, Spring AI 1.0.0 (`ChatClient.Builder.defaultSystem(Resource)`), JUnit 5 + Mockito.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/resources/system-prompt.md` | The system prompt text |
| Modify | `src/main/java/org/daneel/chat/ChatConfig.java` | Inject resource, set as `defaultSystem` on `ChatClient` |

---

## Task 1: Create the system prompt resource

**Files:**
- Create: `src/main/resources/system-prompt.md`

- [ ] **Step 1: Create `system-prompt.md`**

  Create `src/main/resources/system-prompt.md` with exactly this content:

  ```markdown
  You are DaneelClaw, a personal assistant. You are here to help.

  - Be concise and clear in your responses.
  - Be friendly and professional.
  - If a request is unclear, ask for clarification before answering.
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add src/main/resources/system-prompt.md
  git commit -m "feat: add DaneelClaw system prompt resource"
  ```

---

## Task 2: Wire the system prompt into ChatClient

**Files:**
- Modify: `src/main/java/org/daneel/chat/ChatConfig.java`

The current file is:

```java
package org.daneel.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class ChatConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    RestClientCustomizer http11RestClientCustomizer() {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(2));
        return builder -> builder.requestFactory(factory);
    }
}
```

- [ ] **Step 1: Add the `@Value` resource parameter and `defaultSystem` call**

  Replace the file with:

  ```java
  package org.daneel.chat;

  import org.springframework.ai.chat.client.ChatClient;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.boot.web.client.RestClientCustomizer;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.core.io.Resource;
  import org.springframework.http.client.JdkClientHttpRequestFactory;

  import java.net.http.HttpClient;
  import java.time.Duration;

  @Configuration
  class ChatConfig {

      @Bean
      ChatClient chatClient(ChatClient.Builder builder,
                            @Value("classpath:system-prompt.md") Resource systemPrompt) {
          return builder.defaultSystem(systemPrompt).build();
      }

      @Bean
      RestClientCustomizer http11RestClientCustomizer() {
          var httpClient = HttpClient.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
                  .connectTimeout(Duration.ofSeconds(10))
                  .build();
          var factory = new JdkClientHttpRequestFactory(httpClient);
          factory.setReadTimeout(Duration.ofMinutes(2));
          return builder -> builder.requestFactory(factory);
      }
  }
  ```

  Note: `ChatClient.Builder.defaultSystem(Resource)` exists in Spring AI 1.0.0 and reads the resource as UTF-8. No manual file reading needed.

- [ ] **Step 2: Run the full test suite**

  ```bash
  ./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
  ```

  Expected: `Tests run: 5, Failures: 0, Errors: 0` and `BUILD SUCCESS`. The existing tests mock `ChatClient`, so they are unaffected by this bean-construction change. The change must still compile and not break context-free unit tests.

- [ ] **Step 3: Verify the application starts and the prompt loads**

  ```bash
  ./mvnw spring-boot:run > /tmp/daneel-boot.log 2>&1 &
  APP_PID=$!
  sleep 14
  grep -c "APPLICATION FAILED TO START" /tmp/daneel-boot.log || true
  grep -c "Started DaneelclawApplication" /tmp/daneel-boot.log || true
  kill $APP_PID 2>/dev/null
  ```

  Expected: `APPLICATION FAILED TO START` count is `0`, `Started DaneelclawApplication` count is `1`. (If port 8080 is busy, run `lsof -ti:8080 | xargs kill -9` first, or use `./start.sh` which frees the port.) A missing resource would cause `APPLICATION FAILED TO START` with a `FileNotFoundException` for `system-prompt.md` — its absence confirms the resource resolves.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/org/daneel/chat/ChatConfig.java
  git commit -m "feat: apply system-prompt.md as ChatClient default system message"
  ```

---

## Task 3: End-to-end verification (manual, requires LMStudio)

- [ ] **Step 1: Start LMStudio** with a model loaded and the server running on `localhost:1234`.

- [ ] **Step 2: Start the app**

  ```bash
  ./start.sh
  ```

- [ ] **Step 3: Confirm identity in the browser**

  Open `http://localhost:8080`, send "What is your name and what do you do?".
  Expected: the assistant identifies itself as **DaneelClaw**, a personal assistant here to help — confirming the system prompt is applied.

- [ ] **Step 4 (optional): Confirm the file drives behaviour**

  Edit `src/main/resources/system-prompt.md` (e.g., add "Always answer in French."), restart `./start.sh`, and send a message. Expected: behaviour changes accordingly, proving the resource is the source of the prompt.

---

## Self-Review

- **Spec coverage:** system-prompt.md content (Task 1) ✓; `defaultSystem(Resource)` wiring in ChatConfig (Task 2) ✓; fail-fast on missing resource (Task 2 Step 3 verification) ✓; tests unaffected (Task 2 Step 2) ✓; end-to-end identity check (Task 3) ✓.
- **Placeholders:** none — all code blocks complete.
- **Type consistency:** `@Value("classpath:system-prompt.md") Resource systemPrompt` matches the file created in Task 1; `defaultSystem(systemPrompt)` uses the injected parameter; `http11RestClientCustomizer` preserved verbatim.
