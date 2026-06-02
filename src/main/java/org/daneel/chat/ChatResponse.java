package org.daneel.chat;

/**
 * Reply returned by POST /chat.
 *
 * @param message human-readable assistant reply or command confirmation
 * @param action null for a normal reply, "cleared" after /clear, "compacted" after /compact
 */
public record ChatResponse(String message, String action) {}
