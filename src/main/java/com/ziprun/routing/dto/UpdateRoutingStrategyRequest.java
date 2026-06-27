package com.ziprun.routing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Switch the active routing strategy at runtime (no restart).
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateRoutingStrategyRequest {

    @NotBlank
    private String strategy;
}
