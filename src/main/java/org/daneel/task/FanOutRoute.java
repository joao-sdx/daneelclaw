package org.daneel.task;

import lombok.RequiredArgsConstructor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Camel route that consumes fan-out items from the {@code seda:fanout} queue and dispatches each to
 * {@link FanOutRunner}. One consumer keeps sub-runs sequential — a single local model cannot
 * meaningfully parallelize LLM calls.
 */
@Component
@RequiredArgsConstructor
public class FanOutRoute extends RouteBuilder {

  private final FanOutRunner fanOutRunner;

  @Override
  public void configure() {
    onException(Exception.class)
        .handled(true)
        .logHandled(true)
        .logStackTrace(true)
        .log(
            LoggingLevel.ERROR,
            "fanout_item_failed session=${body.sessionId} : ${exception.message}");

    from("seda:fanout?concurrentConsumers=1").routeId("fan-out").bean(fanOutRunner, "run");
  }
}
