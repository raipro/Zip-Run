package com.ziprun.routing;

import com.ziprun.agent.Agent;
import com.ziprun.order.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Load-balancing strategy: recommend the available agent carrying the fewest active
 * orders. Deterministic, dependency-free, and the graceful fallback when the AI strategy
 * is unavailable (T-3).
 */
@Component
public class RuleBasedRoutingStrategy implements RoutingStrategy {

    public static final String NAME = "rule-based";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<RoutingRecommendation> recommend(Order order, List<Agent> availableAgents) {
        if (availableAgents.isEmpty()) {
            return List.of();
        }
        List<Agent> ranked = availableAgents.stream()
                .sorted(Comparator.comparingInt(Agent::getActiveOrderCount)
                        .thenComparing(Agent::getId))
                .toList();

        int minLoad = ranked.get(0).getActiveOrderCount();
        return ranked.stream()
                .map(agent -> toRecommendation(agent, minLoad, ranked.size()))
                .toList();
    }

    private RoutingRecommendation toRecommendation(Agent agent, int minLoad, int candidateCount) {
        int load = agent.getActiveOrderCount();
        // Confidence is highest for the least-loaded agent and tapers as load rises above
        // the minimum. Deterministic, so it doubles as a sane fallback confidence.
        double confidence = Math.max(0.3, 0.9 - 0.1 * (load - minLoad));
        String reasoning = (load == minLoad)
                ? "Rule-based: %s has the fewest active orders (%d) among %d available agent(s), so it has the most spare capacity."
                        .formatted(agent.getName(), load, candidateCount)
                : "Rule-based: %s is carrying %d active order(s) — more loaded than the lightest available agent."
                        .formatted(agent.getName(), load);
        return new RoutingRecommendation(agent, confidence, reasoning);
    }
}
