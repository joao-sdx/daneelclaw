package org.daneel.task;

import java.time.Instant;

public record PlannedTask(
    String id,
    String name,
    String promptFile,
    Instant nextRunAt,
    Integer recurringIntervalMinutes,
    boolean enabled) {}
