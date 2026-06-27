package com.ziprun.order;

import com.ziprun.agent.Agent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {

    /**
     * List orders, optionally filtered by status. The entity graph eagerly fetches the
     * assigned agent so rendering the list never triggers an N+1 lazy load per row.
     */
    @EntityGraph(attributePaths = "assignedAgent")
    List<Order> findByStatus(OrderStatus status);

    @EntityGraph(attributePaths = "assignedAgent")
    List<Order> findAllByOrderByCreatedAtAsc();

    /** Orders still on an agent's plate — the input to the T-4 re-planning loop. */
    List<Order> findByAssignedAgentAndStatusIn(Agent agent, List<OrderStatus> statuses);
}
