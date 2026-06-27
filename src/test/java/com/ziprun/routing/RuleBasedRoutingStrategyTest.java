package com.ziprun.routing;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentStatus;
import com.ziprun.order.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRoutingStrategyTest {

    private final RuleBasedRoutingStrategy strategy = new RuleBasedRoutingStrategy();

    private Agent agent(String id, int load) {
        return new Agent(id, "Agent " + id, load, AgentStatus.AVAILABLE);
    }

    private Order order() {
        return new Order("ORD-1", "test order", agent("AGT-OWNER", 0));
    }

    @Test
    void ranksAvailableAgentsByFewestActiveOrders() {
        List<RoutingRecommendation> recs = strategy.recommend(order(),
                List.of(agent("AGT-A", 3), agent("AGT-B", 0), agent("AGT-C", 1)));

        assertThat(recs).extracting(r -> r.agent().getId())
                .containsExactly("AGT-B", "AGT-C", "AGT-A");
        // Least-loaded agent is the most confident pick.
        assertThat(recs.get(0).confidence()).isGreaterThan(recs.get(2).confidence());
        assertThat(recs.get(0).reasoning()).contains("fewest active orders");
    }

    @Test
    void breaksTiesDeterministicallyById() {
        List<RoutingRecommendation> recs = strategy.recommend(order(),
                List.of(agent("AGT-Z", 1), agent("AGT-A", 1)));

        assertThat(recs).extracting(r -> r.agent().getId()).containsExactly("AGT-A", "AGT-Z");
    }

    @Test
    void returnsEmptyWhenNoAgentsAvailable() {
        assertThat(strategy.recommend(order(), List.of())).isEmpty();
    }

    @Test
    void nameIsStableKey() {
        assertThat(strategy.name()).isEqualTo("rule-based");
    }
}
