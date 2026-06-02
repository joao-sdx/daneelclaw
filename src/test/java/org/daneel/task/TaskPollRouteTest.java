package org.daneel.task;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Reference Camel route test. Demonstrates:
 * <ul>
 *   <li>Standalone {@link DefaultCamelContext} — no Spring Boot startup overhead</li>
 *   <li>{@link AdviceWith} to swap the timer consumer for a controllable {@code direct:} endpoint</li>
 *   <li>Asserting Splitter EIP fan-out behaviour</li>
 *   <li>Asserting per-item error isolation via {@code onException}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TaskPollRouteTest {

    private DefaultCamelContext camelContext;

    @Mock
    private TaskPoller taskPoller;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new TaskPollRoute(taskPoller));
        AdviceWith.adviceWith(camelContext, "task-poll", a ->
                a.replaceFromWith("direct:tick"));
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void split_invokesRunForEachDueTask() throws Exception {
        var t1 = task("t1");
        var t2 = task("t2");
        when(taskPoller.findDue(any(Instant.class))).thenReturn(List.of(t1, t2));

        try (var producer = camelContext.createProducerTemplate()) {
            producer.sendBody("direct:tick", null);
        }

        verify(taskPoller).run(eq(t1), any(Instant.class));
        verify(taskPoller).run(eq(t2), any(Instant.class));
    }

    @Test
    void onException_isolatesFailingTaskFromSiblings() throws Exception {
        var t1 = task("t1");
        var t2 = task("t2");
        when(taskPoller.findDue(any(Instant.class))).thenReturn(List.of(t1, t2));
        doThrow(new RuntimeException("task t1 failed")).when(taskPoller).run(eq(t1), any(Instant.class));

        try (var producer = camelContext.createProducerTemplate()) {
            producer.sendBody("direct:tick", null);
        }

        verify(taskPoller).run(eq(t1), any(Instant.class)); // t1 was attempted
        verify(taskPoller).run(eq(t2), any(Instant.class)); // t2 still ran despite t1 failure
    }

    private PlannedTask task(String id) {
        return new PlannedTask(id, "Task " + id, id + ".md",
                Instant.parse("2026-06-01T14:00:00Z"), 60, true);
    }
}
