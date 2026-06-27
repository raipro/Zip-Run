package com.ziprun.agentic;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentRepository;
import com.ziprun.agent.AgentStatus;
import com.ziprun.order.Order;
import com.ziprun.order.OrderRepository;
import com.ziprun.order.OrderStatus;
import com.ziprun.routing.RoutingContext;
import com.ziprun.suggestion.SuggestionRepository;
import com.ziprun.suggestion.SuggestionService;
import com.ziprun.suggestion.SuggestionStatus;
import com.ziprun.suggestion.TriggerReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The reasoning + acting steps of the agentic loop (T-4): given an agent that went offline,
 * find its stranded orders and queue an {@code AGENT_OFFLINE} reassignment suggestion for
 * each — running the <i>active</i> routing strategy via {@link SuggestionService}.
 *
 * <p>Synchronous and transactional by design: the async/event concern lives in
 * {@link AgentOfflineListener}, which calls this. Keeping the worker as a plain
 * {@code @Transactional} method makes the loop logic directly testable without async timing.
 *
 * <p><b>Idempotency (AGT-4):</b> before creating a re-plan suggestion for an order, skip if a
 * PENDING {@code AGENT_OFFLINE} suggestion already exists for it — so the same agent flipping
 * offline twice (or overlapping triggers) never duplicates.
 *
 * <p><b>No silent drops:</b> each order is handled independently; one failure is logged and
 * the loop continues. AI failures are already absorbed inside the strategy (rule-based
 * fallback), so a re-plan suggestion still surfaces.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReplanningService {

    /** Orders still on an agent's plate (everything except DELIVERED). */
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.ASSIGNED, OrderStatus.REASSIGNMENT_PENDING, OrderStatus.REASSIGNED);

    private final AgentRepository agentRepository;
    private final OrderRepository orderRepository;
    private final SuggestionRepository suggestionRepository;
    private final SuggestionService suggestionService;

    @Transactional
    public ReplanSummary replan(String agentId) {
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            log.warn("Re-plan skipped: agent {} not found", agentId);
            return ReplanSummary.empty();
        }
        // Guard against a stale event: the agent may have come back online before we ran.
        if (agent.getStatus() != AgentStatus.OFFLINE) {
            log.info("Re-plan skipped: agent {} is no longer OFFLINE ({})", agentId, agent.getStatus());
            return ReplanSummary.empty();
        }

        List<Order> stranded = orderRepository.findByAssignedAgentAndStatusIn(agent, ACTIVE_STATUSES);
        int strandedCount = stranded.size();
        log.info("Agent {} offline — {} stranded order(s) to re-plan", agentId, strandedCount);

        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (Order order : stranded) {
            if (suggestionRepository.existsByOrderAndTriggerReasonAndStatus(
                    order, TriggerReason.AGENT_OFFLINE, SuggestionStatus.PENDING)) {
                skipped++;   // idempotent: an open re-plan suggestion already exists
                continue;
            }
            try {
                suggestionService.createSuggestion(order, new RoutingContext.Recovery(agent, strandedCount));
                created++;
            } catch (Exception e) {
                failed++;
                log.error("Re-plan failed for order {} (agent {}): {}", order.getId(), agentId, e.getMessage());
            }
        }

        ReplanSummary summary = new ReplanSummary(created, skipped, failed);
        log.info("Agent {} re-plan complete: {}", agentId, summary);
        return summary;
    }
}
