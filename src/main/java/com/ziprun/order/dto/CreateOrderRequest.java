package com.ziprun.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create an order pre-assigned to an agent (simulates the morning manual assignment).
 *
 * <p>{@code id} is optional — supply one to match the seed's natural-key format
 * ({@code ORD-009}), or leave it blank and the service generates one.
 *
 * <p>Mutable with setters so Jackson can deserialize the request body.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {

    private String id;

    @NotBlank
    private String description;

    @NotBlank
    private String assignedAgentId;
}
