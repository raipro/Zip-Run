package com.ziprun.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Routing configuration. {@code routing.strategy} names the active strategy at startup
 * (env-overridable, e.g. {@code ROUTING_STRATEGY=ai}). It can also be flipped at runtime
 * via {@code PATCH /config/routing-strategy} without a restart — see
 * {@link RoutingStrategyResolver}.
 */
@ConfigurationProperties(prefix = "routing")
public class RoutingProperties {

    /** Name of the strategy active at startup. Defaults to the rule-based fallback. */
    private String strategy = RuleBasedRoutingStrategy.NAME;

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
