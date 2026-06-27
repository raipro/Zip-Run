package com.ziprun.routing;

import java.util.List;

/**
 * The routing contract: given a {@link RoutingContext} (order + available-agent snapshot +
 * situation), return an <b>ordered</b> list of recommendations (best first).
 *
 * <p>Defined before any implementation exists so both callers — the on-demand HTTP
 * endpoint ({@code POST /orders/{id}/suggest}) and the async re-planning loop (T-4) —
 * depend only on this interface. Adding a new strategy (e.g. sprint-2's
 * {@code ZoneAffinityStrategy}) means implementing this and registering the bean; the
 * selection mechanism ({@link RoutingStrategyResolver}) needs no change.
 *
 * <p>The input is a {@code RoutingContext} rather than bare {@code (order, agents)} so the
 * AI strategy gets the trigger context (initial vs recovery) it needs to reason — designed
 * with the AI implementation in mind from the start (T-3).
 *
 * <p>{@link #name()} is the key under which the strategy is registered and selected via
 * config, so it must be stable and unique.
 */
public interface RoutingStrategy {

    /** Stable, unique identifier used for config-driven selection (e.g. "rule-based", "ai"). */
    String name();

    /**
     * Rank the candidate agents for the order in {@code context}. Implementations must not
     * mutate the inputs. An empty result means "no suitable agent" — the caller decides.
     */
    List<RoutingRecommendation> recommend(RoutingContext context);
}
