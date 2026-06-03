package org.daneel.tool.yetiforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class YetiForceCrmClientTest {

  @Mock private HttpClient httpClient;

  private YetiForceCrmClient client;

  @BeforeEach
  void setUp() {
    var props = new YetiForceProperties();
    props.setUrl("http://crm.test");
    props.setApiKey("test-key");
    props.setAppName("app");
    props.setAppPass("app-secret");
    props.setUser("admin");
    props.setPassword("secret");
    client = new YetiForceCrmClient(props, new ObjectMapper(), httpClient);
  }

  @Test
  void listRecords_loginsOnFirstCallAndReturnsList() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = ok("{\"status\":1,\"result\":[{\"id\":\"1\",\"name\":\"Doe\"}]}");
    doReturn(r1).doReturn(r2).when(httpClient).send(any(), any());

    var records = client.listRecords("Leads", null, 20, 0);

    assertThat(records).hasSize(1);
    assertThat(records.getFirst().get("id")).isEqualTo("1");
  }

  @Test
  void listRecords_reusesCachedTokenOnSecondCall() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = ok("{\"status\":1,\"result\":[]}");
    var r3 = ok("{\"status\":1,\"result\":[]}");
    doReturn(r1).doReturn(r2).doReturn(r3).when(httpClient).send(any(), any());

    client.listRecords("Leads", null, 20, 0);
    client.listRecords("Leads", null, 20, 0);

    // 1 login + 2 list calls = 3 total HTTP calls
    verify(httpClient, times(3)).send(any(), any());
  }

  @Test
  void listRecords_on401_reloginsAndRetries() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = status(401);
    var r3 = loginOk("tok2");
    var r4 = ok("{\"status\":1,\"result\":[]}");
    doReturn(r1).doReturn(r2).doReturn(r3).doReturn(r4).when(httpClient).send(any(), any());

    var records = client.listRecords("Leads", null, 20, 0);

    assertThat(records).isEmpty();
  }

  @Test
  void listRecords_on401Twice_throws() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = status(401);
    var r3 = loginOk("tok2");
    var r4 = status(401);
    doReturn(r1).doReturn(r2).doReturn(r3).doReturn(r4).when(httpClient).send(any(), any());

    assertThatThrownBy(() -> client.listRecords("Leads", null, 20, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("auth failed");
  }

  @Test
  void listRecords_on500_throws() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = status(500);
    doReturn(r1).doReturn(r2).when(httpClient).send(any(), any());

    assertThatThrownBy(() -> client.listRecords("Leads", null, 20, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("status=500");
  }

  @Test
  void createRecord_returnsResultMap() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = ok("{\"status\":1,\"result\":{\"id\":\"42\",\"name\":\"Acme\"}}");
    doReturn(r1).doReturn(r2).when(httpClient).send(any(), any());

    var result = client.createRecord("Accounts", Map.of("accountname", "Acme"));

    assertThat(result.get("id")).isEqualTo("42");
  }

  @Test
  void listRecords_normalizesConditionToBareArrayWithCamelCaseFieldName() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = ok("{\"status\":1,\"result\":[]}");
    doReturn(r1).doReturn(r2).when(httpClient).send(any(), any());

    client.listRecords(
        "Contacts",
        "{\"conditions\":[{\"fieldname\":\"lastname\",\"value\":\"Klouz\",\"operator\":\"e\"}]}",
        20,
        0);

    var captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, times(2)).send(captor.capture(), any());
    var condition = captor.getAllValues().get(1).headers().firstValue("x-condition").orElseThrow();
    var node = new ObjectMapper().readTree(condition);
    assertThat(node.isArray()).isTrue();
    assertThat(node.get(0).get("fieldName").asText()).isEqualTo("lastname");
    assertThat(node.get(0).get("value").asText()).isEqualTo("Klouz");
    assertThat(node.get(0).get("operator").asText()).isEqualTo("e");
    assertThat(node.get(0).has("fieldname")).isFalse();
    assertThat(condition).doesNotContain("conditions");
  }

  @Test
  void getFields_returnsResultMap() throws Exception {
    var r1 = loginOk("tok1");
    var r2 =
        ok(
            "{\"status\":1,\"result\":{\"fields\":[{\"name\":\"lastname\",\"label\":\"Last Name\","
                + "\"mandatory\":true}]}}");
    doReturn(r1).doReturn(r2).when(httpClient).send(any(), any());

    var result = client.getFields("Leads");

    assertThat(result).containsKey("fields");
  }

  @Test
  void deleteRecord_sendsDeleteRequest() throws Exception {
    var r1 = loginOk("tok1");
    var r2 = ok("{\"status\":1}");
    doReturn(r1).doReturn(r2).when(httpClient).send(any(), any());

    client.deleteRecord("Leads", "7");

    verify(httpClient, times(2)).send(any(), any());
  }

  // --- helpers ---

  @SuppressWarnings("unchecked")
  private HttpResponse<String> loginOk(String token) {
    var r = (HttpResponse<String>) mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(200);
    when(r.body()).thenReturn("{\"status\":1,\"result\":{\"token\":\"" + token + "\"}}");
    return r;
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> ok(String body) {
    var r = (HttpResponse<String>) mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(200);
    when(r.body()).thenReturn(body);
    return r;
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<String> status(int code) {
    var r = (HttpResponse<String>) mock(HttpResponse.class);
    when(r.statusCode()).thenReturn(code);
    when(r.body()).thenReturn("");
    return r;
  }
}
