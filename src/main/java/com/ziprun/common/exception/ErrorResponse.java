package com.ziprun.common.exception;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error payload returned by {@link GlobalExceptionHandler} for every
 * failure path. Immutable record so the API contract is explicit.
 *
 * @param timestamp when the error was produced (server time, UTC)
 * @param status    HTTP status code
 * @param error     short, machine-friendly code (e.g. NOT_FOUND, VALIDATION_FAILED)
 * @param message   human-readable summary
 * @param path      request path that produced the error
 * @param details   optional field-level messages (used for validation failures)
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, path, details);
    }
}
