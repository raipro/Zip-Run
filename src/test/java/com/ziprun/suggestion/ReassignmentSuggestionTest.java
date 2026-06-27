package com.ziprun.suggestion;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentStatus;
import com.ziprun.common.exception.BusinessRuleException;
import com.ziprun.order.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReassignmentSuggestionTest {

    private final Agent agent = new Agent("AGT-2", "Rahul", 0, AgentStatus.AVAILABLE);
    private final Order order = new Order("ORD-1", "test", new Agent("AGT-1", "Priya", 1, AgentStatus.OFFLINE));

    private ReassignmentSuggestion suggestion(double confidence) {
        return new ReassignmentSuggestion(order, agent, confidence, "because", TriggerReason.INITIAL, "rule-based");
    }

    @Test
    void newSuggestionStartsPending() {
        assertThat(suggestion(0.8).getStatus()).isEqualTo(SuggestionStatus.PENDING);
    }

    @Test
    void confidenceIsClampedToUnitInterval() {
        assertThat(suggestion(1.5).getConfidence()).isEqualTo(1.0);
        assertThat(suggestion(-0.5).getConfidence()).isEqualTo(0.0);
        assertThat(suggestion(0.42).getConfidence()).isEqualTo(0.42);
    }

    @Test
    void accept_setsAccepted() {
        ReassignmentSuggestion s = suggestion(0.8);
        s.accept();
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.ACCEPTED);
    }

    @Test
    void reject_setsRejected() {
        ReassignmentSuggestion s = suggestion(0.8);
        s.reject();
        assertThat(s.getStatus()).isEqualTo(SuggestionStatus.REJECTED);
    }

    @Test
    void cannotChangeStatusOnceClosed() {
        ReassignmentSuggestion s = suggestion(0.8);
        s.accept();
        assertThatThrownBy(s::reject)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already");
    }
}
