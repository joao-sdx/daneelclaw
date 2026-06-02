# Planned & Recurring Task Scheduler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a YAML-backed task scheduler that lets operators define planned/recurring tasks, each backed by a prompt `.md` file with placeholders, triggered autonomously via the existing LLM + tool pipeline.

**Architecture:** `TaskStore` persists `PlannedTask` records to `{tasksDir}/tasks.yml` using a YAML Jackson mapper. `PromptResolver` loads the prompt `.md` file and substitutes four time placeholders. `TaskPoller` runs on a Spring `@Scheduled` interval, triggers due tasks via `chatService.chat()` (Spring AI handles the tool loop), then reschedules recurring tasks by advancing `nextRunAt` in a loop until it is strictly in the future (server-down safety). `TaskController` exposes a REST CRUD API over `TaskStore`.

**Tech Stack:** Spring Boot 3.5.14, `jackson-dataformat-yaml`, `JavaTimeModule`, JUnit 5 + Mockito + `@TempDir`, `@WebMvcTest`.

---

## File Map

| Action | Path |
|--------|------|
| Modify | `pom.xml` |
| Modify | `src/main/java/org/daneel/DaneelclawApplication.java` |
| Modify | `src/main/resources/application.yml` |
| Create | `src/main/java/org/daneel/task/PlannedTask.java` |
| Create | `src/main/java/org/daneel/task/TaskRequest.java` |
| Create | `src/main/java/org/daneel/task/TaskStore.java` |
| Create | `src/main/java/org/daneel/task/PromptResolver.java` |
| Create | `src/main/java/org/daneel/task/TaskPoller.java` |
| Create | `src/main/java/org/daneel/task/TaskController.java` |
| Create | `src/test/java/org/daneel/task/TaskStoreTest.java` |
| Create | `src/test/java/org/daneel/task/PromptResolverTest.java` |
| Create | `src/test/java/org/daneel/task/TaskPollerTest.java` |
| Create | `src/test/java/org/daneel/task/TaskControllerTest.java` |

---

## Task 1: Bootstrap — pom.xml + @EnableScheduling + application.yml

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/org/daneel/DaneelclawApplication.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add `jackson-dataformat-yaml` to `pom.xml`**

  Insert after the `spring-boot-starter-validation` dependency:

  ```xml
  <dependency>
      <groupId>com.fasterxml.jackson.dataformat</groupId>
      <artifactId>jackson-dataformat-yaml</artifactId>
  </dependency>
  ```

  No `<version>` — managed by `spring-boot-dependencies` BOM.

- [ ] **Step 2: Add `@EnableScheduling` to `DaneelclawApplication.java`**

  Replace the file:

  ```java
  package org.daneel;

  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  import org.springframework.scheduling.annotation.EnableScheduling;

  @SpringBootApplication
  @EnableScheduling
  public class DaneelclawApplication {

      public static void main(String[] args) {
          SpringApplication.run(DaneelclawApplication.class, args);
      }
  }
  ```

- [ ] **Step 3: Add scheduler config to `application.yml`**

  Replace the `daneel` block:

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

  daneel:
    tools:
      speak:
        command: "say -v Thomas {text}"
    scheduler:
      tasks-dir: "./tasks"
      check-interval-ms: 60000
  ```

- [ ] **Step 4: Verify compilation**

  ```bash
  ./mvnw compile -q
  ```

  Expected: success.

- [ ] **Step 5: Commit**

  ```bash
  git add pom.xml \
          src/main/java/org/daneel/DaneelclawApplication.java \
          src/main/resources/application.yml
  git commit -m "feat: add jackson-yaml, @EnableScheduling and scheduler config"
  ```

---

## Task 2: Data types — `PlannedTask` and `TaskRequest`

**Files:**
- Create: `src/main/java/org/daneel/task/PlannedTask.java`
- Create: `src/main/java/org/daneel/task/TaskRequest.java`

- [ ] **Step 1: Create `PlannedTask.java`**

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

- [ ] **Step 2: Create `TaskRequest.java`**

  ```java
  package org.daneel.task;

  import jakarta.validation.constraints.NotBlank;
  import jakarta.validation.constraints.NotNull;

  import java.time.Instant;

  public record TaskRequest(
          @NotBlank String name,
          @NotBlank String promptFile,
          @NotNull Instant nextRunAt,
          Integer recurringIntervalMinutes,
          boolean enabled
  ) {}
  ```

- [ ] **Step 3: Verify compilation**

  ```bash
  ./mvnw compile -q
  ```

  Expected: success.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/org/daneel/task/PlannedTask.java \
          src/main/java/org/daneel/task/TaskRequest.java
  git commit -m "feat: add PlannedTask and TaskRequest records"
  ```

---

## Task 3: `TaskStore` — YAML-backed task registry (TDD)

**Files:**
- Create: `src/test/java/org/daneel/task/TaskStoreTest.java`
- Create: `src/main/java/org/daneel/task/TaskStore.java`

- [ ] **Step 1: Write the failing tests**

  Create `src/test/java/org/daneel/task/TaskStoreTest.java`:

  ```java
  package org.daneel.task;

  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;

  import java.nio.file.Path;
  import java.time.Instant;

  import static org.assertj.core.api.Assertions.assertThat;

  class TaskStoreTest {

      @TempDir
      Path tempDir;

      private TaskStore store;

      @BeforeEach
      void setUp() {
          store = new TaskStore(tempDir.toString());
      }

      @Test
      void findAll_emptyWhenFileDoesNotExist() {
          assertThat(store.findAll()).isEmpty();
      }

      @Test
      void save_andFindById() {
          var task = task("t1");
          store.save(task);
          assertThat(store.findById("t1")).contains(task);
      }

      @Test
      void save_persistsToDisk() {
          store.save(task("t1"));
          var store2 = new TaskStore(tempDir.toString());
          assertThat(store2.findById("t1")).isPresent();
      }

      @Test
      void save_replacesExistingTask() {
          store.save(task("t1"));
          var updated = new PlannedTask("t1", "Updated", "t1.md",
                  Instant.parse("2026-06-01T15:00:00Z"), 60, true);
          store.save(updated);
          assertThat(store.findById("t1").map(PlannedTask::name)).contains("Updated");
          assertThat(store.findAll()).hasSize(1);
      }

      @Test
      void delete_removesTask() {
          store.save(task("t1"));
          store.delete("t1");
          assertThat(store.findById("t1")).isEmpty();
          assertThat(store.findAll()).isEmpty();
      }

      @Test
      void findAll_returnsAllTasks() {
          store.save(task("t1"));
          store.save(task("t2"));
          assertThat(store.findAll()).hasSize(2);
      }

      private PlannedTask task(String id) {
          return new PlannedTask(id, "Task " + id, id + ".md",
                  Instant.parse("2026-06-01T14:00:00Z"), 60, true);
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=TaskStoreTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `TaskStore` not found.

- [ ] **Step 3: Create `TaskStore.java`**

  ```java
  package org.daneel.task;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.fasterxml.jackson.databind.SerializationFeature;
  import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
  import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
  import lombok.SneakyThrows;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.stereotype.Component;

  import java.io.File;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.nio.file.StandardCopyOption;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.Optional;

  @Slf4j
  @Component
  public class TaskStore {

      private static final String TASKS_FILE = "tasks.yml";

      private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
              .registerModule(new JavaTimeModule())
              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

      private final String tasksDir;

      public TaskStore(@Value("${daneel.scheduler.tasks-dir:./tasks}") String tasksDir) {
          this.tasksDir = tasksDir;
      }

      public synchronized List<PlannedTask> findAll() {
          var file = tasksFile();
          if (!file.exists()) {
              return new ArrayList<>();
          }
          return read(file);
      }

      public synchronized Optional<PlannedTask> findById(String id) {
          return findAll().stream().filter(t -> t.id().equals(id)).findFirst();
      }

      public synchronized void save(PlannedTask task) {
          var tasks = findAll();
          tasks.removeIf(t -> t.id().equals(task.id()));
          tasks.add(task);
          write(tasks);
      }

      public synchronized void delete(String id) {
          var tasks = findAll();
          tasks.removeIf(t -> t.id().equals(id));
          write(tasks);
      }

      @SneakyThrows
      private List<PlannedTask> read(File file) {
          var wrapper = YAML_MAPPER.readValue(file, TasksWrapper.class);
          return wrapper.tasks() != null ? new ArrayList<>(wrapper.tasks()) : new ArrayList<>();
      }

      @SneakyThrows
      private void write(List<PlannedTask> tasks) {
          var dir = Path.of(tasksDir);
          Files.createDirectories(dir);
          var tmp = dir.resolve("tasks.yml.tmp");
          var target = dir.resolve(TASKS_FILE);
          YAML_MAPPER.writeValue(tmp.toFile(), new TasksWrapper(tasks));
          Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                  StandardCopyOption.ATOMIC_MOVE);
      }

      private File tasksFile() {
          return Path.of(tasksDir, TASKS_FILE).toFile();
      }

      private record TasksWrapper(List<PlannedTask> tasks) {}
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mvnw test -Dtest=TaskStoreTest -q 2>&1 | tail -5
  ```

  Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/org/daneel/task/TaskStore.java \
          src/test/java/org/daneel/task/TaskStoreTest.java
  git commit -m "feat: add TaskStore with YAML persistence"
  ```

---

## Task 4: `PromptResolver` — placeholder substitution (TDD)

**Files:**
- Create: `src/test/java/org/daneel/task/PromptResolverTest.java`
- Create: `src/main/java/org/daneel/task/PromptResolver.java`

- [ ] **Step 1: Write the failing tests**

  Create `src/test/java/org/daneel/task/PromptResolverTest.java`:

  ```java
  package org.daneel.task;

  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;

  import java.io.UncheckedIOException;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.time.Instant;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;

  class PromptResolverTest {

      @TempDir
      Path tempDir;

      private PromptResolver resolver;
      private PlannedTask task;

      @BeforeEach
      void setUp() {
          resolver = new PromptResolver(tempDir.toString());
          task = new PlannedTask("t1", "Test", "task-1.md",
                  Instant.parse("2026-06-01T14:00:00Z"), 60, true);
      }

      @Test
      void resolve_substitutesTriggerTimeGmt() throws Exception {
          Files.writeString(tempDir.resolve("task-1.md"), "Triggered at {trigger_time_gmt}");
          var result = resolver.resolve(task,
                  Instant.parse("2026-06-01T14:00:00Z"),
                  Instant.parse("2026-06-01T14:00:03Z"));
          assertThat(result).contains("2026-06-01T14:00:00Z");
          assertThat(result).doesNotContain("{trigger_time_gmt}");
      }

      @Test
      void resolve_substitutesCurrentTimeGmt() throws Exception {
          Files.writeString(tempDir.resolve("task-1.md"), "Now: {current_time_gmt}");
          var result = resolver.resolve(task,
                  Instant.parse("2026-06-01T14:00:00Z"),
                  Instant.parse("2026-06-01T14:00:05Z"));
          assertThat(result).contains("2026-06-01T14:00:05Z");
          assertThat(result).doesNotContain("{current_time_gmt}");
      }

      @Test
      void resolve_substitutesLocalTimePlaceholders() throws Exception {
          Files.writeString(tempDir.resolve("task-1.md"),
                  "{trigger_time_local} and {current_time_local}");
          var result = resolver.resolve(task,
                  Instant.parse("2026-06-01T14:00:00Z"),
                  Instant.parse("2026-06-01T14:00:03Z"));
          assertThat(result).doesNotContain("{trigger_time_local}");
          assertThat(result).doesNotContain("{current_time_local}");
      }

      @Test
      void resolve_noPlaceholders_returnsContentVerbatim() throws Exception {
          Files.writeString(tempDir.resolve("task-1.md"), "Hello world");
          var result = resolver.resolve(task, Instant.now(), Instant.now());
          assertThat(result).isEqualTo("Hello world");
      }

      @Test
      void resolve_throwsUncheckedIoExceptionWhenFileNotFound() {
          assertThatThrownBy(() -> resolver.resolve(task, Instant.now(), Instant.now()))
                  .isInstanceOf(UncheckedIOException.class);
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=PromptResolverTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `PromptResolver` not found.

- [ ] **Step 3: Create `PromptResolver.java`**

  ```java
  package org.daneel.task;

  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.stereotype.Component;

  import java.io.IOException;
  import java.io.UncheckedIOException;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.time.Instant;
  import java.time.ZoneId;
  import java.time.ZonedDateTime;
  import java.time.format.DateTimeFormatter;

  @Component
  public class PromptResolver {

      private static final DateTimeFormatter LOCAL_FORMAT =
              DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

      private final String tasksDir;

      public PromptResolver(@Value("${daneel.scheduler.tasks-dir:./tasks}") String tasksDir) {
          this.tasksDir = tasksDir;
      }

      public String resolve(PlannedTask task, Instant triggerTime, Instant currentTime) {
          var file = Path.of(tasksDir, task.promptFile());
          try {
              var content = Files.readString(file);
              return content
                      .replace("{trigger_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(triggerTime))
                      .replace("{current_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(currentTime))
                      .replace("{trigger_time_local}", formatLocal(triggerTime))
                      .replace("{current_time_local}", formatLocal(currentTime));
          } catch (IOException e) {
              throw new UncheckedIOException("Cannot read prompt file: " + file, e);
          }
      }

      private String formatLocal(Instant instant) {
          return LOCAL_FORMAT.format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()));
      }
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mvnw test -Dtest=PromptResolverTest -q 2>&1 | tail -5
  ```

  Expected: `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/org/daneel/task/PromptResolver.java \
          src/test/java/org/daneel/task/PromptResolverTest.java
  git commit -m "feat: add PromptResolver with placeholder substitution"
  ```

---

## Task 5: `TaskPoller` — scheduled trigger (TDD)

**Files:**
- Create: `src/test/java/org/daneel/task/TaskPollerTest.java`
- Create: `src/main/java/org/daneel/task/TaskPoller.java`

- [ ] **Step 1: Write the failing tests**

  Create `src/test/java/org/daneel/task/TaskPollerTest.java`:

  ```java
  package org.daneel.task;

  import org.daneel.chat.ChatService;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.time.Instant;
  import java.util.List;

  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  class TaskPollerTest {

      @Mock TaskStore taskStore;
      @Mock PromptResolver promptResolver;
      @Mock ChatService chatService;

      @InjectMocks TaskPoller poller;

      // Server was down from 14:00 to 17:30
      private static final Instant NOW = Instant.parse("2026-06-01T17:30:00Z");

      @BeforeEach
      void setUp() {
          lenient().when(promptResolver.resolve(any(), any(), any())).thenReturn("resolved prompt");
      }

      @Test
      void pollAt_triggersTaskWhenDue() {
          when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, true)));

          poller.pollAt(NOW);

          verify(chatService).chat(startsWith("auto-t1-"), eq("resolved prompt"));
      }

      @Test
      void pollAt_skipsTaskNotYetDue() {
          when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T18:00:00Z", 60, true)));

          poller.pollAt(NOW);

          verifyNoInteractions(chatService);
      }

      @Test
      void pollAt_skipsDisabledTask() {
          when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, false)));

          poller.pollAt(NOW);

          verifyNoInteractions(chatService);
      }

      @Test
      void reschedule_advancesRecurringTaskToNextFutureSlot() {
          // 14:00 + 4x60min = 18:00 is the first slot after 17:30
          when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, true)));

          poller.pollAt(NOW);

          verify(taskStore).save(argThat(t ->
                  t.id().equals("t1") &&
                  t.nextRunAt().equals(Instant.parse("2026-06-01T18:00:00Z")) &&
                  t.enabled()
          ));
      }

      @Test
      void reschedule_disablesOneShotTaskAfterRun() {
          when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", null, true)));

          poller.pollAt(NOW);

          verify(taskStore).save(argThat(t -> t.id().equals("t1") && !t.enabled()));
      }

      @Test
      void pollAt_reschedulesEvenWhenExecutionFails() {
          when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, true)));
          doThrow(new RuntimeException("LLM down")).when(chatService).chat(any(), any());

          poller.pollAt(NOW);

          verify(taskStore).save(any(PlannedTask.class)); // reschedule still happens
      }

      private PlannedTask task(String id, String nextRunAt, Integer intervalMin, boolean enabled) {
          return new PlannedTask(id, "Task " + id, id + ".md",
                  Instant.parse(nextRunAt), intervalMin, enabled);
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=TaskPollerTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `TaskPoller` not found.

- [ ] **Step 3: Create `TaskPoller.java`**

  ```java
  package org.daneel.task;

  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.daneel.chat.ChatService;
  import org.springframework.scheduling.annotation.Scheduled;
  import org.springframework.stereotype.Component;

  import java.time.Instant;
  import java.time.temporal.ChronoUnit;

  @Slf4j
  @Component
  @RequiredArgsConstructor
  public class TaskPoller {

      private final TaskStore taskStore;
      private final PromptResolver promptResolver;
      private final ChatService chatService;

      @Scheduled(fixedRateString = "${daneel.scheduler.check-interval-ms:60000}")
      public void poll() {
          pollAt(Instant.now());
      }

      void pollAt(Instant now) {
          taskStore.findAll().stream()
                  .filter(PlannedTask::enabled)
                  .filter(task -> !now.isBefore(task.nextRunAt()))
                  .forEach(task -> trigger(task, now));
      }

      private void trigger(PlannedTask task, Instant now) {
          try {
              var prompt = promptResolver.resolve(task, task.nextRunAt(), now);
              var sessionId = "auto-" + task.id() + "-" + task.nextRunAt().toEpochMilli();
              log.info("task_starting id={} name={}", task.id(), task.name());
              chatService.chat(sessionId, prompt);
              log.info("task_completed id={} name={}", task.id(), task.name());
          } catch (Exception e) {
              log.error("task_execution_failed id={} name={}", task.id(), task.name(), e);
          } finally {
              reschedule(task, now);
          }
      }

      private void reschedule(PlannedTask task, Instant now) {
          if (task.recurringIntervalMinutes() == null || task.recurringIntervalMinutes() <= 0) {
              taskStore.save(new PlannedTask(task.id(), task.name(), task.promptFile(),
                      task.nextRunAt(), task.recurringIntervalMinutes(), false));
              return;
          }
          var next = task.nextRunAt().plus(task.recurringIntervalMinutes(), ChronoUnit.MINUTES);
          while (!next.isAfter(now)) {
              next = next.plus(task.recurringIntervalMinutes(), ChronoUnit.MINUTES);
          }
          taskStore.save(new PlannedTask(task.id(), task.name(), task.promptFile(),
                  next, task.recurringIntervalMinutes(), task.enabled()));
      }
  }
  ```

- [ ] **Step 4: Run tests to confirm they pass**

  ```bash
  ./mvnw test -Dtest=TaskPollerTest -q 2>&1 | tail -5
  ```

  Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Run full suite to check nothing regressed**

  ```bash
  ./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -8
  ```

  Expected: `BUILD SUCCESS` (existing 24 + new tests all passing).

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/java/org/daneel/task/TaskPoller.java \
          src/test/java/org/daneel/task/TaskPollerTest.java
  git commit -m "feat: add TaskPoller with scheduling and server-down safety"
  ```

---

## Task 6: `TaskController` — REST CRUD (TDD)

**Files:**
- Create: `src/test/java/org/daneel/task/TaskControllerTest.java`
- Create: `src/main/java/org/daneel/task/TaskController.java`

- [ ] **Step 1: Write the failing tests**

  Create `src/test/java/org/daneel/task/TaskControllerTest.java`:

  ```java
  package org.daneel.task;

  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.http.MediaType;
  import org.springframework.test.context.bean.override.mockito.MockitoBean;
  import org.springframework.test.web.servlet.MockMvc;

  import java.time.Instant;
  import java.util.List;
  import java.util.Optional;

  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.verify;
  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(TaskController.class)
  class TaskControllerTest {

      @Autowired MockMvc mockMvc;
      @MockitoBean TaskStore taskStore;

      @Test
      void list_returnsEmptyList() throws Exception {
          when(taskStore.findAll()).thenReturn(List.of());
          mockMvc.perform(get("/tasks"))
                  .andExpect(status().isOk())
                  .andExpect(content().json("[]"));
      }

      @Test
      void list_returnsTasks() throws Exception {
          when(taskStore.findAll()).thenReturn(List.of(task("t1")));
          mockMvc.perform(get("/tasks"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$[0].id").value("t1"));
      }

      @Test
      void get_returnsTask() throws Exception {
          when(taskStore.findById("t1")).thenReturn(Optional.of(task("t1")));
          mockMvc.perform(get("/tasks/t1"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.id").value("t1"));
      }

      @Test
      void get_returns404WhenNotFound() throws Exception {
          when(taskStore.findById("missing")).thenReturn(Optional.empty());
          mockMvc.perform(get("/tasks/missing"))
                  .andExpect(status().isNotFound());
      }

      @Test
      void create_savesTaskAndReturns201() throws Exception {
          mockMvc.perform(post("/tasks")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"name":"My task","promptFile":"my.md",
                                   "nextRunAt":"2026-06-01T14:00:00Z",
                                   "recurringIntervalMinutes":60,"enabled":true}
                                  """))
                  .andExpect(status().isCreated())
                  .andExpect(jsonPath("$.name").value("My task"))
                  .andExpect(jsonPath("$.id").isNotEmpty());
          verify(taskStore).save(any(PlannedTask.class));
      }

      @Test
      void update_updatesExistingTask() throws Exception {
          when(taskStore.findById("t1")).thenReturn(Optional.of(task("t1")));
          mockMvc.perform(put("/tasks/t1")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"name":"Updated","promptFile":"u.md",
                                   "nextRunAt":"2026-06-01T15:00:00Z",
                                   "recurringIntervalMinutes":null,"enabled":false}
                                  """))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.name").value("Updated"));
          verify(taskStore).save(any(PlannedTask.class));
      }

      @Test
      void update_returns404WhenNotFound() throws Exception {
          when(taskStore.findById("missing")).thenReturn(Optional.empty());
          mockMvc.perform(put("/tasks/missing")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"name":"X","promptFile":"x.md",
                                   "nextRunAt":"2026-06-01T14:00:00Z",
                                   "recurringIntervalMinutes":null,"enabled":true}
                                  """))
                  .andExpect(status().isNotFound());
      }

      @Test
      void delete_returns204() throws Exception {
          when(taskStore.findById("t1")).thenReturn(Optional.of(task("t1")));
          mockMvc.perform(delete("/tasks/t1"))
                  .andExpect(status().isNoContent());
          verify(taskStore).delete("t1");
      }

      @Test
      void delete_returns404WhenNotFound() throws Exception {
          when(taskStore.findById("missing")).thenReturn(Optional.empty());
          mockMvc.perform(delete("/tasks/missing"))
                  .andExpect(status().isNotFound());
      }

      private PlannedTask task(String id) {
          return new PlannedTask(id, "Task " + id, id + ".md",
                  Instant.parse("2026-06-01T14:00:00Z"), 60, true);
      }
  }
  ```

- [ ] **Step 2: Run tests to confirm they fail**

  ```bash
  ./mvnw test -Dtest=TaskControllerTest -q 2>&1 | tail -5
  ```

  Expected: compilation error — `TaskController` not found.

- [ ] **Step 3: Create `TaskController.java`**

  ```java
  package org.daneel.task;

  import jakarta.validation.Valid;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;
  import java.util.UUID;

  @Slf4j
  @RestController
  @RequestMapping("/tasks")
  @RequiredArgsConstructor
  public class TaskController {

      private final TaskStore taskStore;

      @GetMapping
      public List<PlannedTask> list() {
          return taskStore.findAll();
      }

      @GetMapping("/{id}")
      public ResponseEntity<PlannedTask> get(@PathVariable String id) {
          return taskStore.findById(id)
                  .map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
      }

      @PostMapping
      @ResponseStatus(HttpStatus.CREATED)
      public PlannedTask create(@Valid @RequestBody TaskRequest request) {
          var task = new PlannedTask(
                  UUID.randomUUID().toString(),
                  request.name(),
                  request.promptFile(),
                  request.nextRunAt(),
                  request.recurringIntervalMinutes(),
                  request.enabled()
          );
          taskStore.save(task);
          log.info("task_created id={} name={}", task.id(), task.name());
          return task;
      }

      @PutMapping("/{id}")
      public ResponseEntity<PlannedTask> update(@PathVariable String id,
                                                 @Valid @RequestBody TaskRequest request) {
          return taskStore.findById(id)
                  .map(existing -> {
                      var updated = new PlannedTask(id, request.name(), request.promptFile(),
                              request.nextRunAt(), request.recurringIntervalMinutes(),
                              request.enabled());
                      taskStore.save(updated);
                      log.info("task_updated id={}", id);
                      return ResponseEntity.ok(updated);
                  })
                  .orElse(ResponseEntity.notFound().build());
      }

      @DeleteMapping("/{id}")
      public ResponseEntity<Void> delete(@PathVariable String id) {
          return taskStore.findById(id)
                  .map(existing -> {
                      taskStore.delete(id);
                      log.info("task_deleted id={}", id);
                      return ResponseEntity.<Void>noContent().build();
                  })
                  .orElse(ResponseEntity.notFound().build());
      }
  }
  ```

- [ ] **Step 4: Run all tests**

  ```bash
  ./mvnw test 2>&1 | grep -E "Tests run:|BUILD" | tail -10
  ```

  Expected: all test classes pass, `BUILD SUCCESS`.
  Approximate totals: 6 TaskStore + 5 PromptResolver + 6 TaskPoller + 9 TaskController + 24 existing = ~50 tests, 0 failures.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/org/daneel/task/TaskController.java \
          src/test/java/org/daneel/task/TaskControllerTest.java
  git commit -m "feat: add TaskController REST CRUD for planned tasks"
  ```

---

## Self-Review

**Spec coverage:**
- `PlannedTask` record (Task 2) ✓
- `TaskRequest` record (Task 2) ✓
- `tasks.yml` in `{tasksDir}` (Task 3 `TaskStore`) ✓
- Prompt files in same directory as tasks.yml (Task 4 `PromptResolver`) ✓
- Four placeholders: `{trigger_time_gmt}`, `{current_time_gmt}`, `{trigger_time_local}`, `{current_time_local}` (Task 4) ✓
- `@Scheduled` trigger, checks `now >= nextRunAt` (Task 5 `TaskPoller`) ✓
- Server-down safety: loop `while (!next.isAfter(now))` (Task 5) ✓
- One-shot tasks disabled after run (Task 5) ✓
- Execution failure still reschedules (Task 5) ✓
- `chatService.chat()` for autonomous session — Spring AI handles tool loop (Task 5) ✓
- REST CRUD POST/GET/PUT/DELETE `/tasks` (Task 6) ✓
- 404 on unknown ID (Task 6) ✓
- UUID generated on POST (Task 6) ✓
- `@EnableScheduling` (Task 1) ✓
- `jackson-dataformat-yaml` dependency (Task 1) ✓
- `daneel.scheduler.tasks-dir` + `check-interval-ms` config (Task 1) ✓

**Placeholders:** None found.

**Type consistency:**
- `TaskPoller` → `PromptResolver.resolve(task, task.nextRunAt(), now)` — matches signature `resolve(PlannedTask, Instant, Instant)` ✓
- `TaskPoller` reschedule creates `new PlannedTask(id, name, promptFile, next, interval, enabled)` — matches 6-param constructor ✓
- `TaskController.create` creates `new PlannedTask(UUID, name, promptFile, nextRunAt, interval, enabled)` ✓
- `pollAt(Instant now)` in test called as `poller.pollAt(NOW)` ✓
- `TaskStore` static `YAML_MAPPER` used in both `read` and `write` ✓
