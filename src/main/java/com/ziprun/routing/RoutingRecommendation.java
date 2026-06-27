package com.ziprun.routing;

import com.ziprun.agent.Agent;

/**
 * One ranked recommendation from a {@link RoutingStrategy}: which agent, how confident
 * the strategy is (0.0–1.0), and a plain-English justification.
 *
 * <p>Internal value object (not an API DTO) — immutable record by design.
 */
public record RoutingRecommendation(Agent agent, double confidence, String reasoning) {
}
