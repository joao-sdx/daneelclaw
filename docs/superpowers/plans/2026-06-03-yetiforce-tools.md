# YetiForce CRM Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 20 dedicated tools enabling full CRUD on YetiForce CRM modules Leads, Accounts, Contacts, and SalesProcesses.

**Architecture:** A single `YetiForceCrmClient` handles two-layer auth (API key for login, session token for all calls, with automatic 401 retry) and all HTTP CRUD. Twenty thin tool classes delegate to it. Field definitions come from a `yetiforce-fields.yml` resource file loaded at startup, used by create/update tools to build their `properties()` lists dynamically — no code change needed when CRM fields change.

**Tech Stack:** Java 21, Spring Boot, `java.net.http.HttpClient`, Jackson (`ObjectMapper` + `YAMLFactory`), Lombok, JUnit 5, Mockito/AssertJ.

---

## File Map

**Create (main):**
- `src/main/java/org/daneel/tool/yetiforce/YetiForceField.java` — record: `(name, type, required, description)`
- `src/main/java/org/daneel/tool/yetiforce/YetiForceProperties.java` — `@ConfigurationProperties(prefix = "daneel.tools.yetiforce")`
- `src/main/java/org/daneel/tool/yetiforce/YetiForceFieldsConfig.java` — loads `yetiforce-fields.yml` from classpath
- `src/main/java/org/daneel/tool/yetiforce/YetiForceCrmClient.java` — HTTP client: login, token cache, CRUD
- `src/main/java/org/daneel/tool/yetiforce/leads/LeadListTool.java`
- `src/main/java/org/daneel/tool/yetiforce/leads/LeadGetTool.java`
- `src/main/java/org/daneel/tool/yetiforce/leads/LeadCreateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/leads/LeadUpdateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/leads/LeadDeleteTool.java`
- `src/main/java/org/daneel/tool/yetiforce/accounts/AccountListTool.java`
- `src/main/java/org/daneel/tool/yetiforce/accounts/AccountGetTool.java`
- `src/main/java/org/daneel/tool/yetiforce/accounts/AccountCreateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/accounts/AccountUpdateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/accounts/AccountDeleteTool.java`
- `src/main/java/org/daneel/tool/yetiforce/contacts/ContactListTool.java`
- `src/main/java/org/daneel/tool/yetiforce/contacts/ContactGetTool.java`
- `src/main/java/org/daneel/tool/yetiforce/contacts/ContactCreateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/contacts/ContactUpdateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/contacts/ContactDeleteTool.java`
- `src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessListTool.java`
- `src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessGetTool.java`
- `src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessCreateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessUpdateTool.java`
- `src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessDeleteTool.java`
- `src/main/resources/yetiforce-fields.yml`

**Create (test):**
- `src/test/java/org/daneel/tool/yetiforce/YetiForceFieldsConfigTest.java`
- `src/test/java/org/daneel/tool/yetiforce/YetiForceCrmClientTest.java`
- `src/test/java/org/daneel/tool/yetiforce/LeadListToolTest.java`
- `src/test/java/org/daneel/tool/yetiforce/LeadGetToolTest.java`
- `src/test/java/org/daneel/tool/yetiforce/LeadCreateToolTest.java`
- `src/test/java/org/daneel/tool/yetiforce/LeadUpdateToolTest.java`
- `src/test/java/org/daneel/tool/yetiforce/LeadDeleteToolTest.java`

**Modify:**
- `src/main/resources/application.yml` — add `daneel.tools.yetiforce.*` keys
- `CLAUDE.md` — document the yetiforce package, env vars, and fields YAML

---

## Task 1: Fields Infrastructure

**Files:**
- Create: `src/main/java/org/daneel/tool/yetiforce/YetiForceField.java`
- Create: `src/main/java/org/daneel/tool/yetiforce/YetiForceFieldsConfig.java`
- Create: `src/main/resources/yetiforce-fields.yml`
- Create: `src/test/java/org/daneel/tool/yetiforce/YetiForceFieldsConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YetiForceFieldsConfigTest {

  @Test
  void getFields_leads_returnsFieldsIncludingRequiredLastname() {
    var config = new YetiForceFieldsConfig();
    var fields = config.getFields("Leads");
    assertThat(fields).isNotEmpty();
    assertThat(fields.stream().anyMatch(f -> f.name().equals("lastname") && f.required())).isTrue();
  }

  @Test
  void getFields_allFourModules_returnNonEmpty() {
    var config = new YetiForceFieldsConfig();
    assertThat(config.getFields("Leads")).isNotEmpty();
    assertThat(config.getFields("Accounts")).isNotEmpty();
    assertThat(config.getFields("Contacts")).isNotEmpty();
    assertThat(config.getFields("SalesProcesses")).isNotEmpty();
  }

  @Test
  void getFields_unknownModule_returnsEmpty() {
    var config = new YetiForceFieldsConfig();
    assertThat(config.getFields("NonExistent")).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails (compile error)**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux new-session -d -s build 2>/dev/null || true
tmux send-keys -t build "./mvnw test -Dtest=YetiForceFieldsConfigTest > $LOG 2>&1" Enter
sleep 15 && tail -20 $LOG
```

Expected: compilation error — `YetiForceFieldsConfig` not found.

- [ ] **Step 3: Create `YetiForceField` record**

```java
// src/main/java/org/daneel/tool/yetiforce/YetiForceField.java
package org.daneel.tool.yetiforce;

public record YetiForceField(String name, String type, boolean required, String description) {}
```

- [ ] **Step 4: Create `yetiforce-fields.yml`**

```yaml
# src/main/resources/yetiforce-fields.yml
# Field names are defaults for standard YetiForce installations.
# Verify and adjust against your instance via Administration → Studio.
Leads:
  - name: lastname
    type: string
    required: true
    description: "Last name"
  - name: firstname
    type: string
    required: false
    description: "First name"
  - name: company
    type: string
    required: false
    description: "Company name"
  - name: email
    type: string
    required: false
    description: "Email address"
  - name: phone
    type: string
    required: false
    description: "Phone number"
  - name: leadsource
    type: string
    required: false
    description: "Lead source (e.g. Web, Cold Call, Partner)"
  - name: description
    type: string
    required: false
    description: "Notes or description"

Accounts:
  - name: accountname
    type: string
    required: true
    description: "Account/company name"
  - name: website
    type: string
    required: false
    description: "Website URL"
  - name: phone
    type: string
    required: false
    description: "Main phone number"
  - name: email1
    type: string
    required: false
    description: "Primary email"
  - name: industry
    type: string
    required: false
    description: "Industry sector"
  - name: description
    type: string
    required: false
    description: "Notes or description"

Contacts:
  - name: lastname
    type: string
    required: true
    description: "Last name"
  - name: firstname
    type: string
    required: false
    description: "First name"
  - name: email
    type: string
    required: false
    description: "Email address"
  - name: phone
    type: string
    required: false
    description: "Phone number"
  - name: title
    type: string
    required: false
    description: "Job title"
  - name: department
    type: string
    required: false
    description: "Department"
  - name: account_id
    type: string
    required: false
    description: "ID of the related Account record"
  - name: description
    type: string
    required: false
    description: "Notes or description"

SalesProcesses:
  - name: subject
    type: string
    required: true
    description: "Process subject/title"
  - name: closingdate
    type: string
    required: false
    description: "Expected closing date (YYYY-MM-DD)"
  - name: amount
    type: string
    required: false
    description: "Deal amount"
  - name: sales_stage
    type: string
    required: false
    description: "Stage (e.g. Prospecting, Proposal, Closed Won)"
  - name: probability
    type: string
    required: false
    description: "Win probability percentage (0-100)"
  - name: account_id
    type: string
    required: false
    description: "ID of the related Account record"
  - name: contact_id
    type: string
    required: false
    description: "ID of the related Contact record"
  - name: description
    type: string
    required: false
    description: "Notes or description"
```

- [ ] **Step 5: Create `YetiForceFieldsConfig`**

```java
// src/main/java/org/daneel/tool/yetiforce/YetiForceFieldsConfig.java
package org.daneel.tool.yetiforce;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class YetiForceFieldsConfig {

  private final Map<String, List<YetiForceField>> fields;

  @SneakyThrows
  public YetiForceFieldsConfig() {
    var mapper = new ObjectMapper(new YAMLFactory());
    try (var stream =
        getClass().getClassLoader().getResourceAsStream("yetiforce-fields.yml")) {
      fields = mapper.readValue(stream, new TypeReference<>() {});
    }
    log.info("yetiforce_fields_loaded modules={}", fields.keySet());
  }

  public List<YetiForceField> getFields(String module) {
    return fields.getOrDefault(module, List.of());
  }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test -Dtest=YetiForceFieldsConfigTest > $LOG 2>&1" Enter
sleep 20 && tail -20 $LOG
```

Expected: `BUILD SUCCESS`, 3 tests passing.

---

## Task 2: Properties and Configuration

**Files:**
- Create: `src/main/java/org/daneel/tool/yetiforce/YetiForceProperties.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Create `YetiForceProperties`**

```java
// src/main/java/org/daneel/tool/yetiforce/YetiForceProperties.java
package org.daneel.tool.yetiforce;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "daneel.tools.yetiforce")
@Validated
@Getter
@Setter
public class YetiForceProperties {

  @NotBlank private String url;
  @NotBlank private String apiKey;
  @NotBlank private String user;
  @NotBlank private String password;
}
```

- [ ] **Step 2: Add YetiForce config block to `application.yml`**

Add under the `daneel.tools` block (after the `dataforseo` block):

```yaml
    yetiforce:
      url: ${YETIFORCE_URL}
      api-key: ${YETIFORCE_API_KEY}
      user: ${YETIFORCE_USER}
      password: ${YETIFORCE_PASSWORD}
```

- [ ] **Step 3: Commit Tasks 1 and 2**

```bash
git add src/main/java/org/daneel/tool/yetiforce/YetiForceField.java \
        src/main/java/org/daneel/tool/yetiforce/YetiForceFieldsConfig.java \
        src/main/java/org/daneel/tool/yetiforce/YetiForceProperties.java \
        src/main/resources/yetiforce-fields.yml \
        src/main/resources/application.yml \
        src/test/java/org/daneel/tool/yetiforce/YetiForceFieldsConfigTest.java
git commit -m "feat: add YetiForce fields config and properties"
```

---

## Task 3: HTTP Client — Auth and Skeleton

**Files:**
- Create: `src/main/java/org/daneel/tool/yetiforce/YetiForceCrmClient.java`
- Create: `src/test/java/org/daneel/tool/yetiforce/YetiForceCrmClientTest.java`

- [ ] **Step 1: Write auth tests**

```java
// src/test/java/org/daneel/tool/yetiforce/YetiForceCrmClientTest.java
package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YetiForceCrmClientTest {

  @Mock private HttpClient httpClient;

  private YetiForceCrmClient client;

  @BeforeEach
  void setUp() {
    var props = new YetiForceProperties();
    props.setUrl("http://crm.test");
    props.setApiKey("test-key");
    props.setUser("admin");
    props.setPassword("secret");
    client = new YetiForceCrmClient(props, new ObjectMapper(), httpClient);
  }

  @Test
  void listRecords_loginsOnFirstCallAndReturnsList() throws Exception {
    when(httpClient.send(any(), any()))
        .thenReturn(loginOk("tok1"))
        .thenReturn(ok("{\"status\":1,\"result\":[{\"id\":\"1\",\"name\":\"Doe\"}]}"));

    var records = client.listRecords("Leads", null, 20, 0);

    assertThat(records).hasSize(1);
    assertThat(records.getFirst().get("id")).isEqualTo("1");
  }

  @Test
  void listRecords_reusesCachedTokenOnSecondCall() throws Exception {
    when(httpClient.send(any(), any()))
        .thenReturn(loginOk("tok1"))
        .thenReturn(ok("{\"status\":1,\"result\":[]}"))
        .thenReturn(ok("{\"status\":1,\"result\":[]}"));

    client.listRecords("Leads", null, 20, 0);
    client.listRecords("Leads", null, 20, 0);

    // 1 login + 2 list calls = 3 total HTTP calls
    verify(httpClient, times(3)).send(any(), any());
  }

  @Test
  void listRecords_on401_reloginsAndRetries() throws Exception {
    when(httpClient.send(any(), any()))
        .thenReturn(loginOk("tok1"))
        .thenReturn(status(401))
        .thenReturn(loginOk("tok2"))
        .thenReturn(ok("{\"status\":1,\"result\":[]}"));

    var records = client.listRecords("Leads", null, 20, 0);

    assertThat(records).isEmpty();
  }

  @Test
  void listRecords_on401Twice_throws() throws Exception {
    when(httpClient.send(any(), any()))
        .thenReturn(loginOk("tok1"))
        .thenReturn(status(401))
        .thenReturn(loginOk("tok2"))
        .thenReturn(status(401));

    assertThatThrownBy(() -> client.listRecords("Leads", null, 20, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth failed");
  }

  @Test
  void listRecords_on500_throws() throws Exception {
    when(httpClient.send(any(), any()))
        .thenReturn(loginOk("tok1"))
        .thenReturn(status(500));

    assertThatThrownBy(() -> client.listRecords("Leads", null, 20, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("status=500");
  }

  @Test
  void createRecord_returnsResultMap() throws Exception {
    when(httpClient.send(any(), any()))
        .thenReturn(loginOk("tok1"))
        .thenReturn(ok("{\"status\":1,\"result\":{\"id\":\"42\",\"name\":\"Acme\"}}"));

    var result = client.createRecord("Accounts", Map.of("accountname", "Acme"));

    assertThat(result.get("id")).isEqualTo("42");
  }

  @Test
  void deleteRecord_sendsDeleteRequest() throws Exception {
    when(httpClient.send(any(), any()))
        .thenReturn(loginOk("tok1"))
        .thenReturn(ok("{\"status\":1}"));

    client.deleteRecord("Leads", "7");

    verify(httpClient, times(2)).send(any(), any());
  }

  // --- helpers ---

  @SuppressWarnings("unchecked")
  private HttpResponse<String> loginOk(String token) throws Exception {
    var r = (HttpResponse<String>) mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(200);
    when(r.body()).thenReturn("{\"status\":1,\"result\":{\"token\":\"" + token + "\"}}");
    return r;
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> ok(String body) throws Exception {
    var r = (HttpResponse<String>) mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(200);
    when(r.body()).thenReturn(body);
    return r;
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> status(int code) throws Exception {
    var r = (HttpResponse<String>) mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(code);
    when(r.body()).thenReturn("");
    return r;
  }
}
```

- [ ] **Step 2: Run test to verify it fails (compile error)**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test -Dtest=YetiForceCrmClientTest > $LOG 2>&1" Enter
sleep 15 && tail -20 $LOG
```

Expected: compilation error — `YetiForceCrmClient` not found.

- [ ] **Step 3: Create `YetiForceCrmClient`**

```java
// src/main/java/org/daneel/tool/yetiforce/YetiForceCrmClient.java
package org.daneel.tool.yetiforce;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class YetiForceCrmClient {

  private static final String API_BASE = "/webservice/WebserviceStandard/";

  private final YetiForceProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private volatile String token;

  public YetiForceCrmClient(YetiForceProperties properties, ObjectMapper objectMapper) {
    this(
        properties,
        objectMapper,
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
  }

  YetiForceCrmClient(
      YetiForceProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  public List<Map<String, Object>> listRecords(
      String module, String conditions, int limit, int offset) throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + module + "/RecordsList"))
            .header("x-row-limit", String.valueOf(limit))
            .header("x-row-offset", String.valueOf(offset))
            .GET();
    if (conditions != null && !conditions.isBlank()) {
      builder.header("x-condition", conditions);
    }
    var raw = execute(builder.build());
    var result = objectMapper.readTree(raw).path("result");
    if (result.isMissingNode() || result.isNull()) {
      return List.of();
    }
    return objectMapper.convertValue(result, new TypeReference<>() {});
  }

  public Map<String, Object> getRecord(String module, String id) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + module + "/Record/" + id))
            .GET()
            .build();
    var raw = execute(request);
    var result = objectMapper.readTree(raw).path("result");
    return objectMapper.convertValue(result, new TypeReference<>() {});
  }

  public Map<String, Object> createRecord(String module, Map<String, Object> data)
      throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + module + "/Record"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(data)))
            .build();
    var raw = execute(request);
    var result = objectMapper.readTree(raw).path("result");
    return objectMapper.convertValue(result, new TypeReference<>() {});
  }

  public Map<String, Object> updateRecord(String module, String id, Map<String, Object> data)
      throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + module + "/Record/" + id))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(data)))
            .build();
    var raw = execute(request);
    var result = objectMapper.readTree(raw).path("result");
    return objectMapper.convertValue(result, new TypeReference<>() {});
  }

  public void deleteRecord(String module, String id) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + module + "/Record/" + id))
            .DELETE()
            .build();
    execute(request);
  }

  private synchronized void login() throws Exception {
    var body =
        objectMapper.writeValueAsString(
            Map.of("userName", properties.getUser(), "password", properties.getPassword()));
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + "Users/Login"))
            .header("X-API-KEY", properties.getApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("YetiForce login failed status=" + response.statusCode());
    }
    token = objectMapper.readTree(response.body()).path("result").path("token").asText();
    log.info("yetiforce_login_success");
  }

  private synchronized void ensureToken() throws Exception {
    if (token == null) {
      login();
    }
  }

  private String execute(HttpRequest request) throws Exception {
    ensureToken();
    var response = sendWithToken(request);
    if (response.statusCode() == 401) {
      log.info("yetiforce_token_expired retrying");
      synchronized (this) {
        token = null;
      }
      ensureToken();
      response = sendWithToken(request);
      if (response.statusCode() == 401) {
        throw new IllegalStateException("YetiForce auth failed after retry");
      }
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("YetiForce error status=" + response.statusCode());
    }
    log.debug("yetiforce_response status={}", response.statusCode());
    return response.body();
  }

  private HttpResponse<String> sendWithToken(HttpRequest request) throws Exception {
    var withToken =
        HttpRequest.newBuilder(request, (n, v) -> true).header("x-token", token).build();
    return httpClient.send(withToken, HttpResponse.BodyHandlers.ofString());
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test -Dtest=YetiForceCrmClientTest > $LOG 2>&1" Enter
sleep 30 && tail -30 $LOG
```

Expected: `BUILD SUCCESS`, all 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/daneel/tool/yetiforce/YetiForceCrmClient.java \
        src/test/java/org/daneel/tool/yetiforce/YetiForceCrmClientTest.java
git commit -m "feat: add YetiForceCrmClient with auth and CRUD"
```

---

## Task 4: Lead Tools

**Files:**
- Create: all 5 `src/main/java/org/daneel/tool/yetiforce/leads/*.java`
- Create: all 5 `src/test/java/org/daneel/tool/yetiforce/Lead*Test.java`

- [ ] **Step 1: Write all 5 lead tool tests**

```java
// src/test/java/org/daneel/tool/yetiforce/LeadListToolTest.java
package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadListTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadListToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadListTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadListTool(client, new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsJsonArray() throws Exception {
    when(client.listRecords("Leads", "", 20, 0))
        .thenReturn(List.of(Map.of("id", "1", "name", "Doe")));

    var result = tool.execute(Map.of());

    assertThat(result).contains("\"id\"").contains("\"1\"");
  }

  @Test
  void execute_withLimitAndOffset_forwardsToClient() throws Exception {
    when(client.listRecords("Leads", "", 5, 10)).thenReturn(List.of());

    tool.execute(Map.of("limit", 5, "offset", 10));

    verify(client).listRecords("Leads", "", 5, 10);
  }

  @Test
  void execute_withConditions_forwardsToClient() throws Exception {
    var conditions = "{\"conditions\":[{\"fieldname\":\"lastname\",\"value\":\"Smith\",\"operator\":\"e\"}]}";
    when(client.listRecords("Leads", conditions, 20, 0)).thenReturn(List.of());

    tool.execute(Map.of("conditions", conditions));

    verify(client).listRecords("Leads", conditions, 20, 0);
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.listRecords(any(), any(), anyInt(), anyInt()))
        .thenThrow(new RuntimeException("connection error"));

    assertThat(tool.execute(Map.of())).startsWith("Error:");
  }
}
```

```java
// src/test/java/org/daneel/tool/yetiforce/LeadGetToolTest.java
package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadGetTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadGetToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadGetTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadGetTool(client, new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsRecord() throws Exception {
    when(client.getRecord("Leads", "42")).thenReturn(Map.of("id", "42", "lastname", "Doe"));

    var result = tool.execute(Map.of("id", "42"));

    assertThat(result).contains("\"42\"");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).isEqualTo("Error: id is required");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.getRecord(any(), any())).thenThrow(new RuntimeException("not found"));

    assertThat(tool.execute(Map.of("id", "99"))).startsWith("Error:");
  }
}
```

```java
// src/test/java/org/daneel/tool/yetiforce/LeadCreateToolTest.java
package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadCreateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadCreateToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadCreateTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadCreateTool(client, new YetiForceFieldsConfig(), new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsCreatedRecord() throws Exception {
    when(client.createRecord(eq("Leads"), any()))
        .thenReturn(Map.of("id", "101", "name", "John Doe"));

    var result = tool.execute(Map.of("lastname", "Doe", "firstname", "John"));

    assertThat(result).contains("101");
  }

  @Test
  void execute_missingRequiredField_returnsError() {
    var params = new HashMap<String, Object>();
    // lastname is required but absent
    assertThat(tool.execute(params)).startsWith("Error: lastname");
  }

  @Test
  void execute_blankOptionalField_notPassedToClient() throws Exception {
    when(client.createRecord(eq("Leads"), any()))
        .thenReturn(Map.of("id", "1", "name", "Doe"));

    tool.execute(Map.of("lastname", "Doe", "firstname", "  "));

    @SuppressWarnings("unchecked")
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(client).createRecord(eq("Leads"), captor.capture());
    assertThat(captor.getValue()).containsKey("lastname");
    assertThat(captor.getValue()).doesNotContainKey("firstname");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    when(client.createRecord(any(), any())).thenThrow(new RuntimeException("server error"));

    assertThat(tool.execute(Map.of("lastname", "Doe"))).startsWith("Error:");
  }
}
```

```java
// src/test/java/org/daneel/tool/yetiforce/LeadUpdateToolTest.java
package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.daneel.tool.yetiforce.leads.LeadUpdateTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadUpdateToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadUpdateTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadUpdateTool(client, new YetiForceFieldsConfig(), new ObjectMapper());
  }

  @Test
  void execute_happyPath_returnsUpdatedId() throws Exception {
    when(client.updateRecord(eq("Leads"), eq("42"), any()))
        .thenReturn(Map.of("id", "42"));

    var result = tool.execute(Map.of("id", "42", "lastname", "Smith"));

    assertThat(result).contains("42");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).isEqualTo("Error: id is required");
  }

  @Test
  void execute_partialUpdate_onlyPassesProvidedFields() throws Exception {
    when(client.updateRecord(eq("Leads"), eq("42"), any()))
        .thenReturn(Map.of("id", "42"));

    tool.execute(Map.of("id", "42", "email", "new@email.com"));

    @SuppressWarnings("unchecked")
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(client).updateRecord(eq("Leads"), eq("42"), captor.capture());
    assertThat(captor.getValue()).containsOnlyKeys("email");
  }
}
```

```java
// src/test/java/org/daneel/tool/yetiforce/LeadDeleteToolTest.java
package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import org.daneel.tool.yetiforce.leads.LeadDeleteTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadDeleteToolTest {

  @Mock private YetiForceCrmClient client;

  private LeadDeleteTool tool;

  @BeforeEach
  void setUp() {
    tool = new LeadDeleteTool(client);
  }

  @Test
  void execute_happyPath_deletesAndReturnsSuccess() throws Exception {
    var result = tool.execute(java.util.Map.of("id", "42"));

    verify(client).deleteRecord("Leads", "42");
    assertThat(result).contains("true");
  }

  @Test
  void execute_missingId_returnsError() {
    var params = new HashMap<String, Object>();
    params.put("id", null);
    assertThat(tool.execute(params)).isEqualTo("Error: id is required");
  }

  @Test
  void execute_clientThrows_returnsError() throws Exception {
    doThrow(new RuntimeException("record not found")).when(client).deleteRecord(any(), any());

    assertThat(tool.execute(java.util.Map.of("id", "99"))).startsWith("Error:");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail (compile error — lead tools not yet created)**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test -Dtest='LeadListToolTest+LeadGetToolTest+LeadCreateToolTest+LeadUpdateToolTest+LeadDeleteToolTest' > $LOG 2>&1" Enter
sleep 20 && tail -20 $LOG
```

Expected: compilation error — `LeadListTool` etc. not found.

- [ ] **Step 3: Create `LeadListTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/leads/LeadListTool.java
package org.daneel.tool.yetiforce.leads;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeadListTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_lead_list";
  }

  @Override
  public String description() {
    return "Lists Lead records from YetiForce CRM. "
        + "Supports optional JSON conditions filter, limit, and offset for pagination. "
        + "Returns a JSON array of records.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "conditions",
            "Optional JSON filter. "
                + "Example: {\"conditions\":[{\"fieldname\":\"lastname\",\"value\":\"Smith\",\"operator\":\"e\"}]}",
            "string",
            false),
        new ToolProperty("limit", "Max records to return (default 20)", "integer", false),
        new ToolProperty("offset", "Pagination offset (default 0)", "integer", false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var conditions = Objects.toString(params.get("conditions"), "");
    var limit = parseIntOrDefault(params.get("limit"), 20);
    var offset = parseIntOrDefault(params.get("offset"), 0);
    try {
      var records = client.listRecords("Leads", conditions, limit, offset);
      log.info("yetiforce_lead_list count={}", records.size());
      return objectMapper.writeValueAsString(records);
    } catch (Exception e) {
      log.warn("yetiforce_lead_list_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }

  private int parseIntOrDefault(Object raw, int def) {
    if (raw == null) {
      return def;
    }
    try {
      return Integer.parseInt(raw.toString());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
```

- [ ] **Step 4: Create `LeadGetTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/leads/LeadGetTool.java
package org.daneel.tool.yetiforce.leads;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeadGetTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_lead_get";
  }

  @Override
  public String description() {
    return "Gets a single Lead record from YetiForce CRM by its ID. Returns full record as JSON.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      var record = client.getRecord("Leads", id.toString());
      log.info("yetiforce_lead_get id={}", id);
      return objectMapper.writeValueAsString(record);
    } catch (Exception e) {
      log.warn("yetiforce_lead_get_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 5: Create `LeadCreateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/leads/LeadCreateTool.java
package org.daneel.tool.yetiforce.leads;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceField;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeadCreateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_lead_create";
  }

  @Override
  public String description() {
    return "Creates a new Lead record in YetiForce CRM. Returns the created record's id and name.";
  }

  @Override
  public List<ToolProperty> properties() {
    return fieldsConfig.getFields("Leads").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), f.required()))
        .toList();
  }

  @Override
  public String execute(Map<String, Object> params) {
    for (var field : fieldsConfig.getFields("Leads")) {
      if (field.required()) {
        var val = params.get(field.name());
        if (val == null || val.toString().isBlank()) {
          return "Error: " + field.name() + " is required";
        }
      }
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Leads")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.createRecord("Leads", data);
      log.info("yetiforce_lead_create id={}", result.get("id"));
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_lead_create_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 6: Create `LeadUpdateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/leads/LeadUpdateTool.java
package org.daneel.tool.yetiforce.leads;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeadUpdateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_lead_update";
  }

  @Override
  public String description() {
    return "Updates an existing Lead record in YetiForce CRM. "
        + "Pass only the fields you want to change alongside the required id.";
  }

  @Override
  public List<ToolProperty> properties() {
    var props = new ArrayList<ToolProperty>();
    props.add(new ToolProperty("id", "Record ID to update", "string", true));
    fieldsConfig.getFields("Leads").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), false))
        .forEach(props::add);
    return props;
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Leads")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.updateRecord("Leads", id.toString(), data);
      log.info("yetiforce_lead_update id={}", id);
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_lead_update_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 7: Create `LeadDeleteTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/leads/LeadDeleteTool.java
package org.daneel.tool.yetiforce.leads;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeadDeleteTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;

  @Override
  public String name() {
    return "yetiforce_lead_delete";
  }

  @Override
  public String description() {
    return "Deletes a Lead record from YetiForce CRM (moves it to trash). "
        + "Returns {\"success\": true} on success.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID to delete", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      client.deleteRecord("Leads", id.toString());
      log.info("yetiforce_lead_delete id={}", id);
      return "{\"success\": true}";
    } catch (Exception e) {
      log.warn("yetiforce_lead_delete_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 8: Run all lead tests to verify they pass**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test -Dtest='LeadListToolTest+LeadGetToolTest+LeadCreateToolTest+LeadUpdateToolTest+LeadDeleteToolTest' > $LOG 2>&1" Enter
sleep 30 && tail -30 $LOG
```

Expected: `BUILD SUCCESS`, all 15 tests passing.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/daneel/tool/yetiforce/leads/ \
        src/test/java/org/daneel/tool/yetiforce/Lead*Test.java
git commit -m "feat: add Lead CRUD tools with tests"
```

---

## Task 5: Account Tools

**Files:** Create all 5 `src/main/java/org/daneel/tool/yetiforce/accounts/*.java`

These mirror the Lead tools exactly — only the module name (`"Accounts"`), class names, tool names (`yetiforce_account_*`), and descriptions change.

- [ ] **Step 1: Create `AccountListTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/accounts/AccountListTool.java
package org.daneel.tool.yetiforce.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountListTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_account_list";
  }

  @Override
  public String description() {
    return "Lists Account records from YetiForce CRM. "
        + "Supports optional JSON conditions filter, limit, and offset for pagination. "
        + "Returns a JSON array of records.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "conditions",
            "Optional JSON filter. "
                + "Example: {\"conditions\":[{\"fieldname\":\"accountname\",\"value\":\"Acme\",\"operator\":\"e\"}]}",
            "string",
            false),
        new ToolProperty("limit", "Max records to return (default 20)", "integer", false),
        new ToolProperty("offset", "Pagination offset (default 0)", "integer", false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var conditions = Objects.toString(params.get("conditions"), "");
    var limit = parseIntOrDefault(params.get("limit"), 20);
    var offset = parseIntOrDefault(params.get("offset"), 0);
    try {
      var records = client.listRecords("Accounts", conditions, limit, offset);
      log.info("yetiforce_account_list count={}", records.size());
      return objectMapper.writeValueAsString(records);
    } catch (Exception e) {
      log.warn("yetiforce_account_list_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }

  private int parseIntOrDefault(Object raw, int def) {
    if (raw == null) {
      return def;
    }
    try {
      return Integer.parseInt(raw.toString());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
```

- [ ] **Step 2: Create `AccountGetTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/accounts/AccountGetTool.java
package org.daneel.tool.yetiforce.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountGetTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_account_get";
  }

  @Override
  public String description() {
    return "Gets a single Account record from YetiForce CRM by its ID. Returns full record as JSON.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      var record = client.getRecord("Accounts", id.toString());
      log.info("yetiforce_account_get id={}", id);
      return objectMapper.writeValueAsString(record);
    } catch (Exception e) {
      log.warn("yetiforce_account_get_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 3: Create `AccountCreateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/accounts/AccountCreateTool.java
package org.daneel.tool.yetiforce.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_account_create";
  }

  @Override
  public String description() {
    return "Creates a new Account record in YetiForce CRM. Returns the created record's id and name.";
  }

  @Override
  public List<ToolProperty> properties() {
    return fieldsConfig.getFields("Accounts").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), f.required()))
        .toList();
  }

  @Override
  public String execute(Map<String, Object> params) {
    for (var field : fieldsConfig.getFields("Accounts")) {
      if (field.required()) {
        var val = params.get(field.name());
        if (val == null || val.toString().isBlank()) {
          return "Error: " + field.name() + " is required";
        }
      }
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Accounts")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.createRecord("Accounts", data);
      log.info("yetiforce_account_create id={}", result.get("id"));
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_account_create_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 4: Create `AccountUpdateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/accounts/AccountUpdateTool.java
package org.daneel.tool.yetiforce.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountUpdateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_account_update";
  }

  @Override
  public String description() {
    return "Updates an existing Account record in YetiForce CRM. "
        + "Pass only the fields you want to change alongside the required id.";
  }

  @Override
  public List<ToolProperty> properties() {
    var props = new ArrayList<ToolProperty>();
    props.add(new ToolProperty("id", "Record ID to update", "string", true));
    fieldsConfig.getFields("Accounts").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), false))
        .forEach(props::add);
    return props;
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Accounts")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.updateRecord("Accounts", id.toString(), data);
      log.info("yetiforce_account_update id={}", id);
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_account_update_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 5: Create `AccountDeleteTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/accounts/AccountDeleteTool.java
package org.daneel.tool.yetiforce.accounts;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeleteTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;

  @Override
  public String name() {
    return "yetiforce_account_delete";
  }

  @Override
  public String description() {
    return "Deletes an Account record from YetiForce CRM (moves it to trash). "
        + "Returns {\"success\": true} on success.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID to delete", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      client.deleteRecord("Accounts", id.toString());
      log.info("yetiforce_account_delete id={}", id);
      return "{\"success\": true}";
    } catch (Exception e) {
      log.warn("yetiforce_account_delete_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 6: Compile-check and commit**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test-compile > $LOG 2>&1" Enter
sleep 30 && tail -10 $LOG
```

Expected: `BUILD SUCCESS`.

```bash
git add src/main/java/org/daneel/tool/yetiforce/accounts/
git commit -m "feat: add Account CRUD tools"
```

---

## Task 6: Contact Tools

**Files:** Create all 5 `src/main/java/org/daneel/tool/yetiforce/contacts/*.java`

- [ ] **Step 1: Create `ContactListTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/contacts/ContactListTool.java
package org.daneel.tool.yetiforce.contacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactListTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_contact_list";
  }

  @Override
  public String description() {
    return "Lists Contact records from YetiForce CRM. "
        + "Supports optional JSON conditions filter, limit, and offset for pagination. "
        + "Returns a JSON array of records.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "conditions",
            "Optional JSON filter. "
                + "Example: {\"conditions\":[{\"fieldname\":\"lastname\",\"value\":\"Smith\",\"operator\":\"e\"}]}",
            "string",
            false),
        new ToolProperty("limit", "Max records to return (default 20)", "integer", false),
        new ToolProperty("offset", "Pagination offset (default 0)", "integer", false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var conditions = Objects.toString(params.get("conditions"), "");
    var limit = parseIntOrDefault(params.get("limit"), 20);
    var offset = parseIntOrDefault(params.get("offset"), 0);
    try {
      var records = client.listRecords("Contacts", conditions, limit, offset);
      log.info("yetiforce_contact_list count={}", records.size());
      return objectMapper.writeValueAsString(records);
    } catch (Exception e) {
      log.warn("yetiforce_contact_list_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }

  private int parseIntOrDefault(Object raw, int def) {
    if (raw == null) {
      return def;
    }
    try {
      return Integer.parseInt(raw.toString());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
```

- [ ] **Step 2: Create `ContactGetTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/contacts/ContactGetTool.java
package org.daneel.tool.yetiforce.contacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactGetTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_contact_get";
  }

  @Override
  public String description() {
    return "Gets a single Contact record from YetiForce CRM by its ID. Returns full record as JSON.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      var record = client.getRecord("Contacts", id.toString());
      log.info("yetiforce_contact_get id={}", id);
      return objectMapper.writeValueAsString(record);
    } catch (Exception e) {
      log.warn("yetiforce_contact_get_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 3: Create `ContactCreateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/contacts/ContactCreateTool.java
package org.daneel.tool.yetiforce.contacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactCreateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_contact_create";
  }

  @Override
  public String description() {
    return "Creates a new Contact record in YetiForce CRM. Returns the created record's id and name.";
  }

  @Override
  public List<ToolProperty> properties() {
    return fieldsConfig.getFields("Contacts").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), f.required()))
        .toList();
  }

  @Override
  public String execute(Map<String, Object> params) {
    for (var field : fieldsConfig.getFields("Contacts")) {
      if (field.required()) {
        var val = params.get(field.name());
        if (val == null || val.toString().isBlank()) {
          return "Error: " + field.name() + " is required";
        }
      }
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Contacts")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.createRecord("Contacts", data);
      log.info("yetiforce_contact_create id={}", result.get("id"));
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_contact_create_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 4: Create `ContactUpdateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/contacts/ContactUpdateTool.java
package org.daneel.tool.yetiforce.contacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactUpdateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_contact_update";
  }

  @Override
  public String description() {
    return "Updates an existing Contact record in YetiForce CRM. "
        + "Pass only the fields you want to change alongside the required id.";
  }

  @Override
  public List<ToolProperty> properties() {
    var props = new ArrayList<ToolProperty>();
    props.add(new ToolProperty("id", "Record ID to update", "string", true));
    fieldsConfig.getFields("Contacts").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), false))
        .forEach(props::add);
    return props;
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("Contacts")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.updateRecord("Contacts", id.toString(), data);
      log.info("yetiforce_contact_update id={}", id);
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_contact_update_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 5: Create `ContactDeleteTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/contacts/ContactDeleteTool.java
package org.daneel.tool.yetiforce.contacts;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactDeleteTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;

  @Override
  public String name() {
    return "yetiforce_contact_delete";
  }

  @Override
  public String description() {
    return "Deletes a Contact record from YetiForce CRM (moves it to trash). "
        + "Returns {\"success\": true} on success.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID to delete", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      client.deleteRecord("Contacts", id.toString());
      log.info("yetiforce_contact_delete id={}", id);
      return "{\"success\": true}";
    } catch (Exception e) {
      log.warn("yetiforce_contact_delete_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 6: Compile-check and commit**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test-compile > $LOG 2>&1" Enter
sleep 30 && tail -10 $LOG
```

Expected: `BUILD SUCCESS`.

```bash
git add src/main/java/org/daneel/tool/yetiforce/contacts/
git commit -m "feat: add Contact CRUD tools"
```

---

## Task 7: SalesProcess Tools

**Files:** Create all 5 `src/main/java/org/daneel/tool/yetiforce/salesprocesses/*.java`

- [ ] **Step 1: Create `SalesProcessListTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessListTool.java
package org.daneel.tool.yetiforce.salesprocesses;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesProcessListTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_sales_process_list";
  }

  @Override
  public String description() {
    return "Lists SalesProcesses records from YetiForce CRM. "
        + "Supports optional JSON conditions filter, limit, and offset for pagination. "
        + "Returns a JSON array of records.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(
        new ToolProperty(
            "conditions",
            "Optional JSON filter. "
                + "Example: {\"conditions\":[{\"fieldname\":\"sales_stage\",\"value\":\"Prospecting\",\"operator\":\"e\"}]}",
            "string",
            false),
        new ToolProperty("limit", "Max records to return (default 20)", "integer", false),
        new ToolProperty("offset", "Pagination offset (default 0)", "integer", false));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var conditions = Objects.toString(params.get("conditions"), "");
    var limit = parseIntOrDefault(params.get("limit"), 20);
    var offset = parseIntOrDefault(params.get("offset"), 0);
    try {
      var records = client.listRecords("SalesProcesses", conditions, limit, offset);
      log.info("yetiforce_sales_process_list count={}", records.size());
      return objectMapper.writeValueAsString(records);
    } catch (Exception e) {
      log.warn("yetiforce_sales_process_list_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }

  private int parseIntOrDefault(Object raw, int def) {
    if (raw == null) {
      return def;
    }
    try {
      return Integer.parseInt(raw.toString());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
```

- [ ] **Step 2: Create `SalesProcessGetTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessGetTool.java
package org.daneel.tool.yetiforce.salesprocesses;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesProcessGetTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_sales_process_get";
  }

  @Override
  public String description() {
    return "Gets a single SalesProcess record from YetiForce CRM by its ID. Returns full record as JSON.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      var record = client.getRecord("SalesProcesses", id.toString());
      log.info("yetiforce_sales_process_get id={}", id);
      return objectMapper.writeValueAsString(record);
    } catch (Exception e) {
      log.warn("yetiforce_sales_process_get_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 3: Create `SalesProcessCreateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessCreateTool.java
package org.daneel.tool.yetiforce.salesprocesses;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesProcessCreateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_sales_process_create";
  }

  @Override
  public String description() {
    return "Creates a new SalesProcess record in YetiForce CRM. Returns the created record's id and name.";
  }

  @Override
  public List<ToolProperty> properties() {
    return fieldsConfig.getFields("SalesProcesses").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), f.required()))
        .toList();
  }

  @Override
  public String execute(Map<String, Object> params) {
    for (var field : fieldsConfig.getFields("SalesProcesses")) {
      if (field.required()) {
        var val = params.get(field.name());
        if (val == null || val.toString().isBlank()) {
          return "Error: " + field.name() + " is required";
        }
      }
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("SalesProcesses")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.createRecord("SalesProcesses", data);
      log.info("yetiforce_sales_process_create id={}", result.get("id"));
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_sales_process_create_failed reason={}", e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 4: Create `SalesProcessUpdateTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessUpdateTool.java
package org.daneel.tool.yetiforce.salesprocesses;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.daneel.tool.yetiforce.YetiForceFieldsConfig;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesProcessUpdateTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;
  private final YetiForceFieldsConfig fieldsConfig;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "yetiforce_sales_process_update";
  }

  @Override
  public String description() {
    return "Updates an existing SalesProcess record in YetiForce CRM. "
        + "Pass only the fields you want to change alongside the required id.";
  }

  @Override
  public List<ToolProperty> properties() {
    var props = new ArrayList<ToolProperty>();
    props.add(new ToolProperty("id", "Record ID to update", "string", true));
    fieldsConfig.getFields("SalesProcesses").stream()
        .map(f -> new ToolProperty(f.name(), f.description(), f.type(), false))
        .forEach(props::add);
    return props;
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    var data = new HashMap<String, Object>();
    for (var field : fieldsConfig.getFields("SalesProcesses")) {
      var val = params.get(field.name());
      if (val != null && !val.toString().isBlank()) {
        data.put(field.name(), val);
      }
    }
    try {
      var result = client.updateRecord("SalesProcesses", id.toString(), data);
      log.info("yetiforce_sales_process_update id={}", id);
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("yetiforce_sales_process_update_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 5: Create `SalesProcessDeleteTool`**

```java
// src/main/java/org/daneel/tool/yetiforce/salesprocesses/SalesProcessDeleteTool.java
package org.daneel.tool.yetiforce.salesprocesses;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daneel.tool.DaneelToolInterface;
import org.daneel.tool.ToolProperty;
import org.daneel.tool.yetiforce.YetiForceCrmClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesProcessDeleteTool implements DaneelToolInterface {

  private final YetiForceCrmClient client;

  @Override
  public String name() {
    return "yetiforce_sales_process_delete";
  }

  @Override
  public String description() {
    return "Deletes a SalesProcess record from YetiForce CRM (moves it to trash). "
        + "Returns {\"success\": true} on success.";
  }

  @Override
  public List<ToolProperty> properties() {
    return List.of(new ToolProperty("id", "Record ID to delete", "string", true));
  }

  @Override
  public String execute(Map<String, Object> params) {
    var id = params.get("id");
    if (id == null || id.toString().isBlank()) {
      return "Error: id is required";
    }
    try {
      client.deleteRecord("SalesProcesses", id.toString());
      log.info("yetiforce_sales_process_delete id={}", id);
      return "{\"success\": true}";
    } catch (Exception e) {
      log.warn("yetiforce_sales_process_delete_failed id={} reason={}", id, e.getMessage());
      return "Error: " + e.getMessage();
    }
  }
}
```

- [ ] **Step 6: Compile-check and commit**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test-compile > $LOG 2>&1" Enter
sleep 30 && tail -10 $LOG
```

Expected: `BUILD SUCCESS`.

```bash
git add src/main/java/org/daneel/tool/yetiforce/salesprocesses/
git commit -m "feat: add SalesProcess CRUD tools"
```

---

## Task 8: License Headers, CLAUDE.md, and Final Test Run

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add license headers to all new Java files**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "task update-license > $LOG 2>&1" Enter
sleep 30 && tail -10 $LOG
```

Expected: license headers added to all new Java files in the `yetiforce` package.

- [ ] **Step 2: Update `CLAUDE.md` — Tool System section**

In the `### Tool System` section, add `yetiforce` to the list of tool domains. In the paragraph listing implemented tools, add after the last entry:

```
`YetiForceCrmClient`-backed CRM tools (package `org.daneel.tool.yetiforce`): `yetiforce_lead_list`, `yetiforce_lead_get`, `yetiforce_lead_create`, `yetiforce_lead_update`, `yetiforce_lead_delete`, `yetiforce_account_list`, `yetiforce_account_get`, `yetiforce_account_create`, `yetiforce_account_update`, `yetiforce_account_delete`, `yetiforce_contact_list`, `yetiforce_contact_get`, `yetiforce_contact_create`, `yetiforce_contact_update`, `yetiforce_contact_delete`, `yetiforce_sales_process_list`, `yetiforce_sales_process_get`, `yetiforce_sales_process_create`, `yetiforce_sales_process_update`, `yetiforce_sales_process_delete`. `YetiForceFieldsConfig` loads `src/main/resources/yetiforce-fields.yml` at startup to provide dynamic field definitions for create/update tools. Auth is two-layer: static API key for login, cached session token for all CRUD calls with automatic 401 retry.
```

- [ ] **Step 3: Update `CLAUDE.md` — Key Configuration section**

Add after the `dataforseo` block:

```
- `daneel.tools.yetiforce.url` — set via `YETIFORCE_URL` env var; base URL of your YetiForce instance
- `daneel.tools.yetiforce.api-key` — set via `YETIFORCE_API_KEY` env var; the X-API-KEY from Integration → Web service - Applications
- `daneel.tools.yetiforce.user` — set via `YETIFORCE_USER` env var; WebserviceStandard username
- `daneel.tools.yetiforce.password` — set via `YETIFORCE_PASSWORD` env var; WebserviceStandard password
- `src/main/resources/yetiforce-fields.yml` — field definitions per module (Leads, Accounts, Contacts, SalesProcesses); edit this file to match your YetiForce instance's actual field names
```

- [ ] **Step 4: Run the full test suite**

```bash
LOG=/tmp/mvn_$(date +%s).log
tmux send-keys -t build "./mvnw test > $LOG 2>&1" Enter
sleep 120 && tail -30 $LOG
```

Expected: `BUILD SUCCESS`. If any new test fails, check `grep -A 10 'FAILED\|ERROR' $LOG`.

- [ ] **Step 5: Final commit**

```bash
git add CLAUDE.md
git add src/main/java/org/daneel/tool/yetiforce/  # picks up license header changes
git commit -m "feat: add license headers and document YetiForce tools in CLAUDE.md"
```
