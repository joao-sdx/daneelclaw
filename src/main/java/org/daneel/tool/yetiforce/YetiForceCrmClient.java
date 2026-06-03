package org.daneel.tool.yetiforce;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class YetiForceCrmClient {

  private static final String API_BASE = "/webservice/WebserviceStandard/";

  private final YetiForceProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private volatile String token;

  @Autowired
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
    var condition = normalizeCondition(conditions);
    if (condition != null) {
      builder.header("x-condition", condition);
    }
    var raw = execute(builder.build());
    var result = objectMapper.readTree(raw).path("result");
    var records = result.path("records");
    if (records.isObject()) {
      Map<String, Map<String, Object>> byId =
          objectMapper.convertValue(records, new TypeReference<>() {});
      return List.copyOf(byId.values());
    }
    if (result.isArray()) {
      return objectMapper.convertValue(result, new TypeReference<>() {});
    }
    return List.of();
  }

  /**
   * YetiForce's {@code x-condition} header expects a single condition object or a bare array of
   * them, each keyed by {@code fieldName}; malformed input yields a 500. Normalize the looser
   * shapes an LLM may produce: unwrap a stray {@code conditions} wrapper, coerce a single object to
   * an array, and rename {@code fieldname} to {@code fieldName}.
   */
  private String normalizeCondition(String conditions) throws Exception {
    if (conditions == null || conditions.isBlank()) {
      return null;
    }
    var node = objectMapper.readTree(conditions);
    var rules = node.has("conditions") ? node.get("conditions") : node;
    var array = objectMapper.createArrayNode();
    if (rules.isArray()) {
      rules.forEach(rule -> array.add(normalizeRule(rule)));
    } else {
      array.add(normalizeRule(rules));
    }
    return objectMapper.writeValueAsString(array);
  }

  private JsonNode normalizeRule(JsonNode rule) {
    if (rule instanceof ObjectNode object && object.has("fieldname") && !object.has("fieldName")) {
      object.set("fieldName", object.get("fieldname"));
      object.remove("fieldname");
    }
    return rule;
  }

  public Map<String, Object> getRecord(String module, String id) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + module + "/Record/" + id))
            .header("x-raw-data", "1")
            .GET()
            .build();
    var raw = execute(request);
    var result = objectMapper.readTree(raw).path("result");
    return objectMapper.convertValue(result, new TypeReference<>() {});
  }

  public Map<String, Object> getFields(String module) throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + module + "/Fields"))
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

  private String basicAuth() {
    var credentials = properties.getAppName() + ":" + properties.getAppPass();
    return "Basic "
        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }

  private synchronized void login() throws Exception {
    var body =
        objectMapper.writeValueAsString(
            Map.of("userName", properties.getUser(), "password", properties.getPassword()));
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(properties.getUrl() + API_BASE + "Users/Login"))
            .header("Authorization", basicAuth())
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
      throw new IllegalStateException(
          "YetiForce error status=" + response.statusCode() + describeError(response.body()));
    }
    log.debug("yetiforce_response status={}", response.statusCode());
    return response.body();
  }

  /**
   * YetiForce returns a JSON error body ({@code {"error":{"message":..,"code":..}}}) on failures —
   * e.g. an unknown field name in a list condition yields a 500 whose message names the bad field.
   * Surface that so the caller (and the LLM) sees the actual reason instead of a bare status code.
   */
  private String describeError(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    try {
      var error = objectMapper.readTree(body).path("error");
      var message = error.path("message");
      if (message.isTextual() && !message.asText().isBlank()) {
        return " " + message.asText();
      }
    } catch (Exception e) {
      log.debug("yetiforce_error_body_unparseable", e);
    }
    return body.length() > 300 ? " " + body.substring(0, 300) : " " + body;
  }

  private HttpResponse<String> sendWithToken(HttpRequest request) throws Exception {
    var authenticated =
        HttpRequest.newBuilder(request, (n, v) -> true)
            .header("Authorization", basicAuth())
            .header("X-API-KEY", properties.getApiKey())
            .header("x-token", token)
            .build();
    return httpClient.send(authenticated, HttpResponse.BodyHandlers.ofString());
  }
}
