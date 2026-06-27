package com.ziprun.routing;

import com.ziprun.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selects the active {@link RoutingStrategy} for both call paths (the HTTP suggest
 * endpoint and the T-4 async loop), and is the single place strategy switchability lives.
 *
 * <p><b>Mechanism:</b> Spring injects every {@code RoutingStrategy} bean; they're indexed
 * here by {@link RoutingStrategy#name()}. The active name starts from
 * {@code routing.strategy} and can be changed at runtime (no restart) via
 * {@link #setActive(String)}. Adding a strategy in sprint 2 is "implement + annotate
 * {@code @Component}" — this class is untouched.
 *
 * <p><b>Startup validation:</b> if the configured name doesn't resolve to a registered
 * strategy, construction fails fast rather than 500-ing on the first request.
 *
 * <p>{@code activeName} is {@code volatile} so a runtime switch is safely visible to the
 * async loop thread.
 */
@Component
@Slf4j
public class RoutingStrategyResolver {

    private final Map<String, RoutingStrategy> strategiesByName;
    private volatile String activeName;

    public RoutingStrategyResolver(List<RoutingStrategy> strategies, RoutingProperties properties) {
        this.strategiesByName = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(RoutingStrategy::name, Function.identity()));

        String configured = properties.getStrategy();
        if (!strategiesByName.containsKey(configured)) {
            throw new IllegalStateException(
                    "Configured routing.strategy '%s' is not a registered strategy. Available: %s"
                            .formatted(configured, strategiesByName.keySet()));
        }
        this.activeName = configured;
        log.info("Routing strategy resolver initialised. Active='{}', available={}",
                activeName, strategiesByName.keySet());
    }

    /** The currently active strategy — used identically by HTTP and async callers. */
    public RoutingStrategy active() {
        return strategiesByName.get(activeName);
    }

    public String activeName() {
        return activeName;
    }

    public Set<String> available() {
        return strategiesByName.keySet();
    }

    /** Switch the active strategy at runtime. Validated against registered strategies. */
    public void setActive(String name) {
        if (!strategiesByName.containsKey(name)) {
            throw new BusinessRuleException(
                    "Unknown routing strategy '%s'. Available: %s".formatted(name, strategiesByName.keySet()));
        }
        log.info("Switching active routing strategy '{}' -> '{}'", activeName, name);
        this.activeName = name;
    }
}
