package org.daneel.tool.error;

import java.time.Instant;

public record ToolError(String id, String toolName, String message, Instant occurredAt) {}
