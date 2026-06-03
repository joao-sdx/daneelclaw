# YetiForce CRM Tools — Design Spec

**Date:** 2026-06-03  
**Scope:** Add 20 dedicated tools enabling the daneelclaw LLM agent to perform full CRUD on four YetiForce CRM modules: Leads, Accounts, Contacts, SalesProcesses.

---

## 1. Context

Daneelclaw tools follow the `DaneelToolInterface` contract (`name`, `description`, `properties`, `execute`). External API integrations use a shared `Client` + `Properties` + individual tool classes (see `org.daneel.tool.seo` for the established pattern). The `ToolSelector` SLM pre-selects relevant tools per message, so adding 20 tools does not overload the main model.

---

## 2. Package Structure

```
org.daneel.tool.yetiforce/
  YetiForceProperties           ← @ConfigurationProperties: url, apiKey, user, password
  YetiForceCrmClient            ← HTTP client: login, token cache, all CRUD methods
  YetiForceFieldsConfig         ← loads yetiforce-fields.yml at startup
  leads/
    LeadListTool
    LeadGetTool
    LeadCreateTool
    LeadUpdateTool
    LeadDeleteTool
  accounts/
    AccountListTool
    AccountGetTool
    AccountCreateTool
    AccountUpdateTool
    AccountDeleteTool
  contacts/
    ContactListTool
    ContactGetTool
    ContactCreateTool
    ContactUpdateTool
    ContactDeleteTool
  salesprocesses/
    SalesProcessListTool
    SalesProcessGetTool
    SalesProcessCreateTool
    SalesProcessUpdateTool
    SalesProcessDeleteTool
```

`YetiForceCrmClient` is the sole HTTP-speaking class. All 20 tools delegate to it. `YetiForceFieldsConfig` is injected only into create/update tools.

---

## 3. Authentication

YetiForce uses a two-layer auth:

1. **API key** (`X-API-KEY` header) — static, from config, sent only on the login request.
2. **Session token** (`x-token` header) — obtained by `POST /webservice/WebserviceStandard/Users/Login` with body `{userName, password}`. Cached in `YetiForceCrmClient` as a `volatile String`.

**Token lifecycle:**
- First call: no token → login → cache → proceed.
- Subsequent calls: attach cached token. On 401 → re-login once → retry. If still 401 → throw `IllegalStateException`.
- `login()` is `synchronized` to prevent concurrent callers from triggering simultaneous re-logins.

All CRUD methods go through a private `execute(request)` helper that handles the attach → call → retry-on-401 cycle.

**Login endpoint:**
```
POST {url}/webservice/WebserviceStandard/Users/Login
Headers: X-API-KEY: {apiKey}
Body: {"userName": "{user}", "password": "{password}"}
Response: {"status": 1, "result": {"token": "..."}}
```

---

## 4. Configuration

### `application.yml`
```yaml
daneel:
  tools:
    yetiforce:
      url: ${YETIFORCE_URL}
      api-key: ${YETIFORCE_API_KEY}
      user: ${YETIFORCE_USER}
      password: ${YETIFORCE_PASSWORD}
```

### `YetiForceProperties`
```java
@Component
@ConfigurationProperties(prefix = "daneel.tools.yetiforce")
@Validated @Getter @Setter
public class YetiForceProperties {
    @NotBlank private String url;
    @NotBlank private String apiKey;
    @NotBlank private String user;
    @NotBlank private String password;
}
```

### `src/main/resources/yetiforce-fields.yml`

Defines fields per module. Create/update tools read this at runtime via `YetiForceFieldsConfig` to build their `properties()` list dynamically. No code change needed when fields are added or renamed.

```yaml
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

`YetiForceFieldsConfig` reads this file using Jackson `YAMLFactory` and exposes `getFields(String moduleName)` returning `List<YetiForceField>` where `YetiForceField` is a record `(String name, String type, boolean required, String description)` defined in `org.daneel.tool.yetiforce`.

> **Note:** Field names in `yetiforce-fields.yml` are best-guess defaults based on standard YetiForce installations. Verify and adjust them against your actual instance via Administration → Studio or the YetiForce field editor before using create/update tools.

---

## 5. Tool Inventory

**Naming:** `yetiforce_<module>_<operation>` where module is `lead`, `account`, `contact`, `sales_process`.

### List tools (`yetiforce_lead_list`, etc.)
Parameters:
- `conditions` — optional JSON filter object (passed as `x-condition` header). Example: `{"conditions": [{"fieldname": "lastname", "value": "Smith", "operator": "e"}]}`. Verify the exact format against your YetiForce instance's WebserviceStandard docs — operator codes and structure may vary.
- `limit` — integer, default 20
- `offset` — integer, default 0

Returns: JSON array of records, each with `id`, `name`, and all returned fields.

### Get tools (`yetiforce_lead_get`, etc.)
Parameters:
- `id` — required, record ID

Returns: JSON object with full record fields.

### Create tools (`yetiforce_lead_create`, etc.)
Parameters: one `ToolProperty` per field defined in `yetiforce-fields.yml` for that module (required flags respected).

Returns: `{"id": "...", "name": "..."}`

### Update tools (`yetiforce_lead_update`, etc.)
Parameters:
- `id` — required, record ID
- One `ToolProperty` per YAML field (all optional — only pass fields to change)

Returns: `{"id": "..."}`

### Delete tools (`yetiforce_lead_delete`, etc.)
Parameters:
- `id` — required, record ID

Returns: `{"success": true}`

---

## 6. `YetiForceCrmClient` API Surface

```java
List<Map<String, Object>> listRecords(String module, String conditions, int limit, int offset)
Map<String, Object> getRecord(String module, String id)
Map<String, Object> createRecord(String module, Map<String, Object> data)
Map<String, Object> updateRecord(String module, String id, Map<String, Object> data)
void deleteRecord(String module, String id)
```

Internal `HttpClient` (Java 11+ `java.net.http`) with `HTTP_1_1` version, matching the `DataForSeoClient` pattern. JSON serialised/deserialised via injected `ObjectMapper`.

---

## 7. Error Handling

- All tool `execute()` methods catch `Exception` and return `"Error: ..."` — consistent with existing tools. `ToolRegistrar.call()` provides an additional `ErrorStore` backstop.
- `YetiForceCrmClient` throws `IllegalStateException` on non-2xx responses (excluding the 401-retry path).
- Missing required parameters return `"Error: <field> is required"` before making any HTTP call.

---

## 8. Testing

- **`YetiForceCrmClientTest`** — mocks `HttpClient`; covers: successful login + token reuse, 401 retry + re-login success, 401 retry + second failure throws, non-401 HTTP error throws.
- **`YetiForceFieldsConfigTest`** — loads a test YAML fixture from `src/test/resources`; verifies `getFields()` returns correct entries and handles unknown module gracefully.
- **Per-operation representative tests** — one test class per operation type using Lead as the representative module:
  - `LeadListToolTest` — mocks client, verifies condition/limit/offset forwarding
  - `LeadGetToolTest` — verifies id forwarding, missing-id error
  - `LeadCreateToolTest` — verifies required field validation, data map construction
  - `LeadUpdateToolTest` — verifies id required, partial field update
  - `LeadDeleteToolTest` — verifies id required, success response

---

## 9. CLAUDE.md Updates

- Add `yetiforce` package to Architecture → Tool System section
- Add env vars (`YETIFORCE_URL`, `YETIFORCE_API_KEY`, `YETIFORCE_USER`, `YETIFORCE_PASSWORD`) to Key Configuration section
- Add `yetiforce-fields.yml` note to Key Configuration section
