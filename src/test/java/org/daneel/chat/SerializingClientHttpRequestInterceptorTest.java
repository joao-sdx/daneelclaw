package org.daneel.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class SerializingClientHttpRequestInterceptorTest {

  /** Two threads both call intercept(); asserts the maximum observed concurrency is exactly 1. */
  @Test
  void intercept_serializesRequests() throws Exception {
    var semaphore = new Semaphore(1, true);
    var interceptor = new SerializingClientHttpRequestInterceptor(semaphore);

    var maxConcurrent = new AtomicInteger(0);
    var concurrent = new AtomicInteger(0);
    var bothStarted = new CountDownLatch(2);
    var bothDone = new CountDownLatch(2);

    ClientHttpRequestExecution slowExecution =
        (request, body) -> {
          bothStarted.countDown();
          var current = concurrent.incrementAndGet();
          maxConcurrent.accumulateAndGet(current, Math::max);
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          concurrent.decrementAndGet();
          return okResponse();
        };

    var request = stubRequest();
    var t1 =
        new Thread(
            () -> {
              try {
                interceptor.intercept(request, new byte[0], slowExecution);
              } catch (IOException e) {
                throw new RuntimeException(e);
              } finally {
                bothDone.countDown();
              }
            });
    var t2 =
        new Thread(
            () -> {
              try {
                interceptor.intercept(request, new byte[0], slowExecution);
              } catch (IOException e) {
                throw new RuntimeException(e);
              } finally {
                bothDone.countDown();
              }
            });

    t1.start();
    t2.start();
    bothDone.await();

    assertThat(maxConcurrent.get()).as("at most one execution should run at a time").isEqualTo(1);
  }

  /** Verifies the response body is fully readable after intercept returns. */
  @Test
  void intercept_buffersResponseBody() throws Exception {
    var semaphore = new Semaphore(1, true);
    var interceptor = new SerializingClientHttpRequestInterceptor(semaphore);
    var payload = "hello".getBytes();

    ClientHttpRequestExecution execution = (request, body) -> okResponseWithBody(payload);

    var response = interceptor.intercept(stubRequest(), new byte[0], execution);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().readAllBytes()).isEqualTo(payload);
  }

  private static HttpRequest stubRequest() {
    return new MockClientHttpRequest(
        HttpMethod.POST, URI.create("http://localhost:1234/v1/chat/completions"));
  }

  private static ClientHttpResponse okResponse() {
    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
  }

  private static ClientHttpResponse okResponseWithBody(byte[] body) {
    return new MockClientHttpResponse(body, HttpStatus.OK);
  }
}
