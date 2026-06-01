package org.daneel.task;

import org.daneel.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskPollerTest {

    @Mock TaskStore taskStore;
    @Mock PromptResolver promptResolver;
    @Mock ChatService chatService;

    @InjectMocks TaskPoller poller;

    // Simulate server down from 14:00 to 17:30
    private static final Instant NOW = Instant.parse("2026-06-01T17:30:00Z");

    @BeforeEach
    void setUp() {
        lenient().when(promptResolver.resolve(any(), any(), any())).thenReturn("resolved prompt");
    }

    @Test
    void pollAt_triggersTaskWhenDue() {
        when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, true)));

        poller.pollAt(NOW);

        verify(chatService).chat(startsWith("auto-t1-"), eq("resolved prompt"));
    }

    @Test
    void pollAt_skipsTaskNotYetDue() {
        when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T18:00:00Z", 60, true)));

        poller.pollAt(NOW);

        verifyNoInteractions(chatService);
    }

    @Test
    void pollAt_skipsDisabledTask() {
        when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, false)));

        poller.pollAt(NOW);

        verifyNoInteractions(chatService);
    }

    @Test
    void reschedule_advancesRecurringTaskToNextFutureSlot() {
        // 14:00 + 4x60min = 18:00 is the first slot strictly after 17:30
        when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, true)));

        poller.pollAt(NOW);

        verify(taskStore).save(argThat(t ->
                t.id().equals("t1") &&
                t.nextRunAt().equals(Instant.parse("2026-06-01T18:00:00Z")) &&
                t.enabled()
        ));
    }

    @Test
    void reschedule_disablesOneShotTaskAfterRun() {
        when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", null, true)));

        poller.pollAt(NOW);

        verify(taskStore).save(argThat(t -> t.id().equals("t1") && !t.enabled()));
    }

    @Test
    void pollAt_reschedulesEvenWhenExecutionFails() {
        when(taskStore.findAll()).thenReturn(List.of(task("t1", "2026-06-01T14:00:00Z", 60, true)));
        doThrow(new RuntimeException("LLM down")).when(chatService).chat(any(), any());

        poller.pollAt(NOW);

        verify(taskStore).save(any(PlannedTask.class)); // reschedule still happens
    }

    private PlannedTask task(String id, String nextRunAt, Integer intervalMin, boolean enabled) {
        return new PlannedTask(id, "Task " + id, id + ".md",
                Instant.parse(nextRunAt), intervalMin, enabled);
    }
}
