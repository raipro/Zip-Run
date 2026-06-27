package com.ziprun.common.exception;

/**
 * Thrown when a requested domain entity does not exist (maps to HTTP 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** Convenience for the common "{Type} with id '{id}' not found" shape. */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("%s with id '%s' not found".formatted(resource, id));
    }
}
