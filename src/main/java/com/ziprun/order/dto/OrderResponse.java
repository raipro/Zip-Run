package com.ziprun.order.dto;

import com.ziprun.order.Order;
import com.ziprun.order.OrderStatus;
import lombok.Getter;

import java.time.Instant;

/**
 * API view of an order. Flattens the assigned agent to id + name so the UI dispatch
 * list needs no extra lookup.
 */
@Getter
public class OrderResponse {

    private final String id;
    private final String description;
    private final String assignedAgentId;
    private final String assignedAgentName;
    private final OrderStatus status;
    private final Instant createdAt;

    public OrderResponse(String id, String description, String assignedAgentId,
                         String assignedAgentName, OrderStatus status, Instant createdAt) {
        this.id = id;
        this.description = description;
        this.assignedAgentId = assignedAgentId;
        this.assignedAgentName = assignedAgentName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static OrderResponse from(Order order) {
        var agent = order.getAssignedAgent();
        return new OrderResponse(
                order.getId(),
                order.getDescription(),
                agent != null ? agent.getId() : null,
                agent != null ? agent.getName() : null,
                order.getStatus(),
                order.getCreatedAt());
    }
}
