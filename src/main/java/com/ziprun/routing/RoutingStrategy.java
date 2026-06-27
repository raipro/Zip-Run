package com.ziprun.routing;

import com.ziprun.agent.Agent;
import com.ziprun.order.Order;

import java.util.List;

/**
 * The routing contract: given an order and the roster of currently-available agents,
 * return an <b>ordered</b> list of recommendations (best first).
 *
 * <p>Defined before any implementation exists so both callers — the on-demand HTTP
 * endpoint ({@code POST /orders/{id}/suggest}) and the async re-planning loop (T-4) —
 * depend only on this interface. Adding a new strategy (e.g. sprint-2's
 * {@code ZoneAffinityStrategy}) means implementing this and registering the bean; the
 * selection mechanism ({@link RoutingStrategyResolver}) needs no change.
 *
 * <p>{@link #name()} is the key under which the strategy is registered and selected via
 * config, so it must be stable and unique.
 */
public interface RoutingStrategy {

    /** Stable, unique identifier used for config-driven selection (e.g. "rule-based", "ai"). */
    String name();

    /**
     * Rank the candidate agents for this order. Implementations must not mutate the
     * inputs. An empty result means "no suitable agent" — the caller decides what to do.
     */
    List<RoutingRecommendation> recommend(Order order, List<Agent> availableAgents);
}
