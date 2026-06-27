package com.ziprun.ai;

/**
 * The parsed LLM payload: {@code {"agentId":..,"confidence":..,"reasoning":..}}.
 * Still raw/untrusted at this point — the agent id is validated against the live roster
 * before use.
 */
public record AiRecommendation(String agentId, double confidence, String reasoning) {
}
