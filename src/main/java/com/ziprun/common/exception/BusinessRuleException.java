package com.ziprun.common.exception;

/**
 * Thrown when a request is well-formed but violates a domain rule — most importantly
 * an illegal state-machine transition (e.g. delivering an order that was never
 * reassigned, or accepting an already-closed suggestion). Maps to HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
