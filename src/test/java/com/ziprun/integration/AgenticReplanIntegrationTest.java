package com.ziprun.integration;

import com.ziprun.suggestion.ReassignmentSuggestion;
import com.ziprun.suggestion.SuggestionRepository;
import com.ziprun.suggestion.SuggestionStatus;
import com.ziprun.suggestion.TriggerReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the agentic loop wiring: a real {@code PATCH /agents/{id}/status=OFFLINE}
 * must return immediately and, asynchronously, queue AGENT_OFFLINE suggestions that appear on
 * the next poll — nobody clicks a button.
 *
 * <p>Not {@code @Transactional} (the {@code @TransactionalEventListener(AFTER_COMMIT)} only
 * fires on a real commit), so it polls for the async result and uses {@code @DirtiesContext}
 * to rebuild the seeded DB afterwards. AGT-003 holds ORD-003 and ORD-007 in the seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@DirtiesContext
class AgenticReplanIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SuggestionRepository suggestionRepository;

    @Test
    void patchOffline_returnsImmediately_andAsyncallyQueuesAgentOfflineSuggestions() throws Exception {
        long start = System.currentTimeMillis();
        mockMvc.perform(patch("/agents/{id}/status", "AGT-003")
                        .contentType("application/json")
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;

        // The PATCH must not block on re-planning.
        assertThat(elapsed).isLessThan(2000);

        // Suggestions appear asynchronously — poll until the loop has run.
        List<ReassignmentSuggestion> pending = awaitPending(2, 10_000);

        assertThat(pending).hasSize(2)
                .allMatch(s -> s.getTriggerReason() == TriggerReason.AGENT_OFFLINE)
                .extracting(s -> s.getOrder().getId())
                .containsExactlyInAnyOrder("ORD-003", "ORD-007");
    }

    private List<ReassignmentSuggestion> awaitPending(int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        List<ReassignmentSuggestion> pending = List.of();
        while (System.currentTimeMillis() < deadline) {
            pending = suggestionRepository.findByOptionalStatus(SuggestionStatus.PENDING);
            if (pending.size() >= expected) {
                return pending;
            }
            Thread.sleep(150);
        }
        return pending;
    }
}
