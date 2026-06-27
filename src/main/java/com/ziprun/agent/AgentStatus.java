package com.ziprun.agent;

/**
 * Availability of a delivery agent.
 *
 * <p>{@link #OFFLINE} is the trigger for the agentic re-planning loop (T-4): when an
 * agent flips OFFLINE, its in-flight orders are stranded and need reassignment.
 */
public enum AgentStatus {
    AVAILABLE,
    BUSY,
    OFFLINE
}
