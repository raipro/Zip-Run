package com.ziprun.suggestion;

/**
 * Lifecycle of a reassignment suggestion: {@code PENDING → ACCEPTED | REJECTED}.
 * The system only ever proposes (PENDING); ops makes the irreversible call.
 */
public enum SuggestionStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
