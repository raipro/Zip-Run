package com.ziprun.agentic;

import com.ziprun.agent.AgentService;
import com.ziprun.agent.AgentStatus;
import com.ziprun.suggestion.ReassignmentSuggestion;
import com.ziprun.suggestion.SuggestionRepository;
import com.ziprun.suggestion.SuggestionStatus;
import com.ziprun.suggestion.TriggerReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests of the re-planning logic, invoked directly (synchronously) so there's
 * no async timing — the async/event wiring is covered separately in
 * {@link AgenticReplanIntegrationTest}. Uses the default rule-based strategy. AGT-001 holds
 * ORD-001, ORD-002, ORD-008 in the seed.
 */
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class ReplanningServiceTest {

    @Autowired
    private ReplanningService replanningService;
    @Autowired
    private AgentService agentService;
    @Autowired
    private SuggestionRepository suggestionRepository;

    @Test
    void replan_queuesAgentOfflineSuggestionForEachStrandedOrder() {
        agentService.updateStatus("AGT-001", AgentStatus.OFFLINE);

        ReplanSummary summary = replanningService.replan("AGT-001");

        assertThat(summary.created()).isEqualTo(3);
        assertThat(summary.skipped()).isZero();
        List<ReassignmentSuggestion> pending = suggestionRepository.findByOptionalStatus(SuggestionStatus.PENDING);
        assertThat(pending).hasSize(3)
                .allMatch(s -> s.getTriggerReason() == TriggerReason.AGENT_OFFLINE)
                .extracting(s -> s.getOrder().getId())
                .containsExactlyInAnyOrder("ORD-001", "ORD-002", "ORD-008");
    }

    @Test
    void replan_isIdempotent_onRepeatedTrigger() {
        agentService.updateStatus("AGT-001", AgentStatus.OFFLINE);
        replanningService.replan("AGT-001");

        ReplanSummary second = replanningService.replan("AGT-001");

        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(3);
        // Still exactly three — no duplicates from the repeated trigger.
        assertThat(suggestionRepository.findByOptionalStatus(SuggestionStatus.PENDING)).hasSize(3);
    }

    @Test
    void replan_skipsWhenAgentNotOffline() {
        // AGT-002 is AVAILABLE in the seed — a stale/incorrect trigger must do nothing.
        ReplanSummary summary = replanningService.replan("AGT-002");

        assertThat(summary).isEqualTo(ReplanSummary.empty());
        assertThat(suggestionRepository.findByOptionalStatus(SuggestionStatus.PENDING)).isEmpty();
    }

    @Test
    void replan_skipsUnknownAgent() {
        assertThat(replanningService.replan("GHOST")).isEqualTo(ReplanSummary.empty());
    }
}
