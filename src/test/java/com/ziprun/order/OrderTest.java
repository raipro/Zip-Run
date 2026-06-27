package com.ziprun.order;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentStatus;
import com.ziprun.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Agent agent(String id) {
        return new Agent(id, "Agent " + id, 0, AgentStatus.AVAILABLE);
    }

    private Order assignedOrder() {
        return new Order("ORD-1", "test", agent("AGT-1"));
    }

    @Test
    void newOrderStartsAssigned() {
        assertThat(assignedOrder().getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    }

    @Test
    void markReassignmentPending_fromAssigned_transitions() {
        Order order = assignedOrder();
        order.markReassignmentPending();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
    }

    @Test
    void markReassignmentPending_isIdempotentWhenAlreadyPending() {
        Order order = assignedOrder();
        order.markReassignmentPending();
        order.markReassignmentPending();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNMENT_PENDING);
    }

    @Test
    void reassignTo_fromPending_setsNewAgentAndReassigned() {
        Order order = assignedOrder();
        order.markReassignmentPending();
        Agent newAgent = agent("AGT-2");

        order.reassignTo(newAgent);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REASSIGNED);
        assertThat(order.getAssignedAgent()).isEqualTo(newAgent);
    }

    @Test
    void reassignTo_fromAssigned_isRejected() {
        Order order = assignedOrder();
        assertThatThrownBy(() -> order.reassignTo(agent("AGT-2")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be reassigned");
    }

    @Test
    void markDelivered_fromPending_isRejected() {
        Order order = assignedOrder();
        order.markReassignmentPending();
        assertThatThrownBy(order::markDelivered)
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markDelivered_fromAssigned_isAllowed() {
        Order order = assignedOrder();
        order.markDelivered();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void generateId_matchesSeedFormat() {
        assertThat(Order.generateId()).matches("ORD-[0-9A-F]{8}");
    }
}
