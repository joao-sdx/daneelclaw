package org.daneel.chat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/**
 * Serializes all outbound LLM HTTP calls through a single-permit fair semaphore. LMStudio (and
 * similar local inference servers) serve only one request at a time; concurrent requests cause
 * failures. The permit is held only for the duration of one HTTP round-trip (acquire → execute →
 * buffer response body → release), so it is free during tool execution that happens between the two
 * LLM calls within a single {@code ChatClient.call()} invocation. This allows fan-out sub-runs to
 * proceed while the main request is blocked in {@code spawn_per_item.awaitCompletion}.
 *
 * <p>Toggled by {@code daneel.llm.serialize-calls} in {@code application.yml}.
 */
@Slf4j
@RequiredArgsConstructor
class SerializingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  private final Semaphore semaphore;

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for LLM serialization permit", e);
    }
    try {
      log.debug("llm_permit_acquired uri={}", request.getURI());
      var response = execution.execute(request, body);
      var buffered = new BufferedClientHttpResponse(response);
      log.debug(
          "llm_permit_releasing uri={} status={}", request.getURI(), buffered.getStatusCode());
      return buffered;
    } finally {
      semaphore.release();
    }
  }

  /**
   * Eagerly reads the response body into memory so the semaphore can be released before the caller
   * consumes the stream. Without buffering, releasing the permit while the body is still streaming
   * would allow a second request to start while the first inference is not yet fully delivered.
   */
  private static class BufferedClientHttpResponse implements ClientHttpResponse {

    private final HttpStatusCode statusCode;
    private final String statusText;
    private final HttpHeaders headers;
    private final byte[] body;

    BufferedClientHttpResponse(ClientHttpResponse delegate) throws IOException {
      this.statusCode = delegate.getStatusCode();
      this.statusText = delegate.getStatusText();
      this.headers = delegate.getHeaders();
      this.body = StreamUtils.copyToByteArray(delegate.getBody());
      delegate.close();
    }

    @Override
    public HttpStatusCode getStatusCode() {
      return statusCode;
    }

    @Override
    public String getStatusText() {
      return statusText;
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {
      // nothing to close — body is in memory
    }
  }
}
