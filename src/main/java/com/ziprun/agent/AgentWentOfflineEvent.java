package com.ziprun.agent;

/**
 * Published when an agent transitions to OFFLINE. Decouples the status update (T-1) from the
 * agentic re-planning loop (T-4): the agent service just announces "this happened", and a
 * listener reacts off the request path.
 *
 * <p>Carries only the id — the listener reloads fresh state in its own post-commit
 * transaction. Lives in the {@code agent} package so the publisher needs no cross-package
 * dependency (the {@code agentic} loop depends on {@code agent}, never the reverse).
 *
 * <p>A sprint-3 {@code SlaBreachEvent} would be a sibling here, consumed the same way.
 */
public record AgentWentOfflineEvent(String agentId) {
}
