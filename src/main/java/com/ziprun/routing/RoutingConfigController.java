package com.ziprun.routing;

import com.ziprun.routing.dto.RoutingStrategyView;
import com.ziprun.routing.dto.UpdateRoutingStrategyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inspect and switch the active routing strategy at runtime — demonstrates config-driven
 * switchability with no restart and no code change (T-2).
 */
@RestController
@RequestMapping("/config/routing-strategy")
@RequiredArgsConstructor
public class RoutingConfigController {

    private final RoutingStrategyResolver resolver;

    @GetMapping
    public RoutingStrategyView current() {
        return new RoutingStrategyView(resolver.activeName(), resolver.available());
    }

    @PatchMapping
    public RoutingStrategyView switchStrategy(@Valid @RequestBody UpdateRoutingStrategyRequest request) {
        resolver.setActive(request.getStrategy());
        return new RoutingStrategyView(resolver.activeName(), resolver.available());
    }
}
