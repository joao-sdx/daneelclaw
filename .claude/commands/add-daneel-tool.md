---
description: Scaffold and implement a new LLM-callable DaneelToolInterface tool in this project
allowed-tools: Read, Edit, Write, Bash, Agent
---

## Context

DaneelClaw exposes capabilities to the LLM via `DaneelToolInterface` implementations.
`ToolRegistrar` auto-discovers all `@Component` beans at startup — no manual wiring needed.

Key files to know before you start:
- `src/main/java/org/daneel/tool/DaneelToolInterface.java` — the contract
- `src/main/java/org/daneel/tool/ToolProperty.java` — param descriptor record
- `src/main/java/org/daneel/tool/ToolRegistrar.java` — auto-wires all tools into ChatClient
- Any existing tool (e.g. `FileReadTool`, `CsvHeadersTool`) for a concrete reference implementation
- `CLAUDE.md` — must be updated after adding the tool

## Your Task

Implement a new `DaneelToolInterface` tool. If the user didn't specify the tool, ask:
- What should the tool do? (one sentence)
- What should its `name()` return? (snake_case string, e.g. `my_tool`)
- What params does it need?

### Steps

1. **Decide package** — tools live in `src/main/java/org/daneel/tool/<pkg>/`. Use an existing
   sub-package if the tool fits (e.g. `csv`, `file`, `spawn`) or create a new one.

2. **Implement the tool class**

   ```java
   @Component
   @RequiredArgsConstructor
   public class MyTool implements DaneelToolInterface {

     // inject SandboxFileSystem if tool touches the filesystem
     // inject CsvSupport if tool works with CSV
     // inject ObjectMapper (Spring bean) if tool returns JSON
     // inject TaskStore / PromptCatalog as needed

     @Override public String name() { return "my_tool"; }

     @Override public String description() { return "..."; }

     @Override
     public List<ToolProperty> properties() {
       return List.of(
           new ToolProperty("param", "description", "string", true),
           new ToolProperty("items", "...", "array",  false)  // flat array of scalars only
       );
     }

     @Override
     @SneakyThrows
     public String execute(Map<String, Object> params) {
       var raw = params.get("param");
       if (raw == null || raw.toString().isBlank()) {
         return "Error: param is required.";
       }
       // array params:
       // if (!(params.get("items") instanceof List<?> list)) return "Error: ...";
       try {
         // ... implementation
         return "Done: ...";
       } catch (SandboxAccessException e) { return "Error: " + e.getMessage(); }
         catch (IOException e)            { return "Error: " + e.getMessage(); }
     }
   }
   ```

3. **Guardrails — common mistakes to avoid**

   - **`@SneakyThrows` on `execute()`** — required for consistency; never `throws IOException`
   - **`"Error: ..."` prefix** — all failure returns must start with `"Error: "`
   - **Flat arrays only** — `ToolRegistrar.buildSchema` supports `items: {type: scalar}` only;
     no nested arrays or array-of-objects params; use two parallel arrays for mappings
   - **Array params pattern** — `params.get("x") instanceof List<?> list` (not `.get("x")` cast)
   - **No ObjectMapper unless JSON output** — only inject if the tool actually calls
     `objectMapper.writeValueAsString(...)`; don't add it speculatively
   - **Filesystem tools** — resolve paths via `sandbox.resolve(userPath)` first, catch
     `SandboxAccessException` before generic `IOException`
   - **ToolProperty record** — `(name, description, type, required)` for scalars;
     `(name, description, "array", required, itemType)` for arrays (5-arg constructor)

4. **Write tests** in `src/test/java/org/daneel/tool/`

   Test file: `MyToolTest.java` (flat in `org.daneel.tool`, not sub-packaged)

   ```java
   class MyToolTest {
     @TempDir Path tempDir;
     private MyTool tool;

     @BeforeEach
     void setUp() {
       tool = new MyTool(new SandboxFileSystem(tempDir.toString()) /*, other deps */);
     }

     @Test void execute_happyPath() { ... }
     @Test void execute_missingRequiredParam_returnsError() { ... }
     @Test void execute_traversalAttempt_returnsError() {
       // always test "../escape" path if tool touches filesystem
       var result = tool.execute(Map.of("path", "../etc/passwd"));
       assertThat(result).startsWith("Error:");
     }
   }
   ```

   Coverage checklist:
   - [ ] Happy path
   - [ ] Each required param missing → `"Error: ..."`
   - [ ] Path traversal (`../`) → `"Error: ..."` (if filesystem tool)
   - [ ] File not found → `"Error: ..."` (if filesystem tool)
   - [ ] Validation errors (wrong count, bad format, etc.)

5. **Update CLAUDE.md**

   Add the tool to the "Implemented tools" list in the Tool System section:
   ```
   `MyTool` (name `my_tool`: one-line description).
   ```

6. **Build and verify**

   ```bash
   LOG=/tmp/mvn_$(date +%s).log
   tmux new-session -d -s build 2>/dev/null || true
   tmux send-keys -t build "./mvnw test > $LOG 2>&1" Enter
   # wait, then:
   tail -50 $LOG | grep -E 'Tests run|BUILD|ERROR'
   ```

   All tests must pass before committing.

7. **Commit**

   ```bash
   git add src/main/java/org/daneel/tool/<pkg>/MyTool.java \
           src/test/java/org/daneel/tool/MyToolTest.java \
           CLAUDE.md
   git commit -m "feat: add <tool_name> tool"
   ```

---
*Generated by /reflect-skills from 3 session patterns*
