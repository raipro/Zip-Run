package com.ziprun.suggestion;

/**
 * Why a suggestion was created. This field threads the domain model to the agentic loop
 * and to the UI re-plan badge:
 *
 * <ul>
 *   <li>{@link #INITIAL} — ops asked for a suggestion on demand (POST /orders/{id}/suggest).</li>
 *   <li>{@link #AGENT_OFFLINE} — the agentic loop produced it automatically because an
 *       agent went offline (T-4). This is what the UI badges as an agentic re-plan.</li>
 * </ul>
 *
 * <p>A sprint-3 {@code SLA_BREACH} value would slot in here without disturbing callers.
 */
public enum TriggerReason {
    INITIAL,
    AGENT_OFFLINE
}
