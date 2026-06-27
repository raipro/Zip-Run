package com.ziprun.routing;

import com.ziprun.agent.Agent;
import com.ziprun.order.Order;

import java.util.List;

/**
 * The full input to a routing decision: the order, the available-agent snapshot, and —
 * crucially for the AI strategy — whether this is a first assignment or a recovery from an
 * agent going offline.
 *
 * <p>Introduced so the routing contract carries the <i>situation</i>, not just the data.
 * The rule-based strategy ignores {@code recovery}; the AI strategy uses it to choose
 * between two genuinely different prompts (T-3). A sprint-3 SLA-breach trigger would add a
 * new recovery flavour here without changing the interface.
 *
 * <p>Lives in {@code routing} (depends only on {@code order}/{@code agent}); the AI package
 * depends on routing, never the reverse.
 */
public record RoutingContext(Order order, List<Agent> availableAgents, Recovery recovery) {

    /** Re-plan details, present only when recovering from an unavailable agent. */
    public record Recovery(Agent unavailableAgent, int strandedOrderCount) {
    }

    public boolean isRecovery() {
        return recovery != null;
    }

    public static RoutingContext initial(Order order, List<Agent> availableAgents) {
        return new RoutingContext(order, availableAgents, null);
    }

    public static RoutingContext recovery(Order order, List<Agent> availableAgents,
                                          Agent unavailableAgent, int strandedOrderCount) {
        return new RoutingContext(order, availableAgents,
                new Recovery(unavailableAgent, strandedOrderCount));
    }
}
