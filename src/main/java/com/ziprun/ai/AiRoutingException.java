package com.ziprun.ai;

/**
 * Raised when the LLM path fails in a way specific to AI routing — unparseable response,
 * missing fields, or a hallucinated agent id. Caught by {@link AiRoutingStrategy}, which
 * falls back to the rule-based strategy.
 */
public class AiRoutingException extends RuntimeException {

    public AiRoutingException(String message) {
        super(message);
    }

    public AiRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
