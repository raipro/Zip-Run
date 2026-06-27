package com.ziprun.agentic;

import com.ziprun.agent.AgentWentOfflineEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The "observe" step of the agentic loop: reacts to an agent going offline and triggers
 * re-planning — off the request path.
 *
 * <p><b>Why event + AFTER_COMMIT + {@code @Async}, not a scheduled poller:</b> the loop must
 * fire because something <i>changed</i>, not because a timer ticked. {@code AFTER_COMMIT}
 * ensures the OFFLINE status (and the agent's orders) are committed and visible before we
 * read them; {@code @Async} runs the work on the {@code replan-} pool so
 * {@code PATCH /agents/{id}/status} returns immediately. The actual transactional work lives
 * in {@link ReplanningService}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AgentOfflineListener {

    private final ReplanningService replanningService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgentWentOffline(AgentWentOfflineEvent event) {
        log.info("Agentic loop triggered for offline agent {}", event.agentId());
        try {
            ReplanSummary summary = replanningService.replan(event.agentId());
            log.info("Agentic re-plan for agent {} finished: {}", event.agentId(), summary);
        } catch (Exception e) {
            // Last-resort guard so an async failure is surfaced in logs, never swallowed.
            log.error("Agentic re-plan for agent {} failed unexpectedly", event.agentId(), e);
        }
    }
}
