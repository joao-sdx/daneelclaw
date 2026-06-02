package org.daneel.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record TaskRequest(
    @NotBlank String name,
    @NotBlank String promptFile,
    @NotNull Instant nextRunAt,
    Integer recurringIntervalMinutes,
    boolean enabled) {}
