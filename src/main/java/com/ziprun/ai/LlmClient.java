package com.ziprun.ai;

/**
 * Sends a prompt to the configured LLM and returns its raw text response.
 *
 * <p>Extracted as an interface so the AI strategy depends on an abstraction — the real
 * {@link LLMGateway} handles provider wire formats, while tests substitute a stub without
 * any HTTP. Implementations throw on transport/parse errors; the caller owns fallback.
 */
public interface LlmClient {

    String callLLM(String prompt);
}
