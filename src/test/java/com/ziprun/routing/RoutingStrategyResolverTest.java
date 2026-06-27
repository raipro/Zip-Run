package com.ziprun.routing;

import com.ziprun.agent.Agent;
import com.ziprun.common.exception.BusinessRuleException;
import com.ziprun.order.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingStrategyResolverTest {

    /** Minimal second strategy so switching has somewhere to go. */
    private static class StubStrategy implements RoutingStrategy {
        private final String name;
        StubStrategy(String name) { this.name = name; }
        public String name() { return name; }
        public List<RoutingRecommendation> recommend(Order order, List<Agent> agents) { return List.of(); }
    }

    private RoutingProperties props(String strategy) {
        RoutingProperties p = new RoutingProperties();
        p.setStrategy(strategy);
        return p;
    }

    @Test
    void activatesConfiguredStrategyAtStartup() {
        var resolver = new RoutingStrategyResolver(
                List.of(new RuleBasedRoutingStrategy(), new StubStrategy("ai")), props("rule-based"));

        assertThat(resolver.activeName()).isEqualTo("rule-based");
        assertThat(resolver.active()).isInstanceOf(RuleBasedRoutingStrategy.class);
        assertThat(resolver.available()).containsExactlyInAnyOrder("rule-based", "ai");
    }

    @Test
    void failsFastWhenConfiguredStrategyIsUnknown() {
        assertThatThrownBy(() -> new RoutingStrategyResolver(
                List.of(new RuleBasedRoutingStrategy()), props("does-not-exist")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    void switchesActiveStrategyAtRuntime() {
        var resolver = new RoutingStrategyResolver(
                List.of(new RuleBasedRoutingStrategy(), new StubStrategy("ai")), props("rule-based"));

        resolver.setActive("ai");

        assertThat(resolver.activeName()).isEqualTo("ai");
        assertThat(resolver.active().name()).isEqualTo("ai");
    }

    @Test
    void rejectsSwitchToUnknownStrategy() {
        var resolver = new RoutingStrategyResolver(
                List.of(new RuleBasedRoutingStrategy()), props("rule-based"));

        assertThatThrownBy(() -> resolver.setActive("ghost"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ghost");
    }
}
