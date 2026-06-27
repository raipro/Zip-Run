package com.ziprun.order;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentRepository;
import com.ziprun.common.exception.ResourceNotFoundException;
import com.ziprun.order.dto.CreateOrderRequest;
import com.ziprun.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Order lifecycle operations. Keeps persistence + the assigned-agent load invariant in
 * one place; routing logic deliberately lives elsewhere (the routing engine, T-2).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;

    /** Create an order pre-assigned to an agent and bump that agent's active load. */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        Agent agent = agentRepository.findById(request.getAssignedAgentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Agent", request.getAssignedAgentId()));

        String id = (request.getId() == null || request.getId().isBlank())
                ? Order.generateId()
                : request.getId();

        Order order = new Order(id, request.getDescription(), agent);
        agent.incrementLoad();
        // saveAndFlush so the @CreationTimestamp is populated before we map the response.
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }

    /** List orders, optionally filtered by status. */
    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        List<Order> orders = (status == null)
                ? orderRepository.findAllByOrderByCreatedAtAsc()
                : orderRepository.findByStatus(status);
        return orders.stream().map(OrderResponse::from).toList();
    }
}
