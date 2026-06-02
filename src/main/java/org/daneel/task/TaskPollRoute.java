package org.daneel.task;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Reference Camel route for task scheduling. Demonstrates:
 *
 * <ul>
 *   <li>{@code timer://} source endpoint with a {@code {{property}}} placeholder bridged from
 *       Spring config
 *   <li>Processor to capture one shared {@code now} per tick as an exchange property
 *   <li>Splitter EIP — one exchange per due task, processed sequentially
 *   <li>Route-level {@code onException} for per-task error isolation
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TaskPollRoute extends RouteBuilder {

  private final TaskPoller taskPoller;

  @Override
  public void configure() {
    onException(Exception.class)
        .handled(true)
        .logHandled(true)
        .logStackTrace(true)
        .log(LoggingLevel.ERROR, "task_execution_failed task=${body} : ${exception.message}");

    from("timer:taskPoll?period={{daneel.scheduler.check-interval-ms:60000}}")
        .routeId("task-poll")
        .process(e -> e.setProperty("now", Instant.now())) // one shared now per tick
        .bean(taskPoller, "findDue") // List<PlannedTask> → body
        .split(body()) // one exchange per due task
        .bean(taskPoller, "run");
  }
}
