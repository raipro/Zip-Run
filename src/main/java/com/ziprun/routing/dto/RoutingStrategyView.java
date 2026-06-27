package com.ziprun.routing.dto;

import lombok.Getter;

import java.util.Set;

/**
 * Current routing configuration: which strategy is active and what's available to switch to.
 */
@Getter
public class RoutingStrategyView {

    private final String active;
    private final Set<String> available;

    public RoutingStrategyView(String active, Set<String> available) {
        this.active = active;
        this.available = available;
    }
}
