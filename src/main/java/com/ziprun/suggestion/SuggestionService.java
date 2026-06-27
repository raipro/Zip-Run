package com.ziprun.suggestion;

import com.ziprun.agent.Agent;
import com.ziprun.agent.AgentRepository;
import com.ziprun.agent.AgentStatus;
import com.ziprun.common.exception.BusinessRuleException;
import com.ziprun.common.exception.ResourceNotFoundException;
import com.ziprun.order.Order;
import com.ziprun.order.OrderRepository;
import com.ziprun.routing.RoutingContext;
import com.ziprun.routing.RoutingRecommendation;
import com.ziprun.routing.RoutingStrategy;
import com.ziprun.routing.RoutingStrategyResolver;
import com.ziprun.suggestion.dto.SuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Suggestion lifecycle: produce a reassignment suggestion by running the active routing
 * strategy, and the human checkpoint where ops accepts or rejects it.
 *
 * <p>Routing <i>decisions</i> live in the {@link RoutingStrategy} implementations and
 * strategy <i>selection</i> in the {@link RoutingStrategyResolver}; this service only
 * orchestrates — run the strategy, enforce the one-PENDING-per-order invariant, persist.
 * {@link #createSuggestion} is shared by the HTTP path and the T-4 async loop.
 */
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final RoutingStrategyResolver routingResolver;

    @Transactional(readOnly = true)
    public List<SuggestionResponse> list(SuggestionStatus status) {
        return suggestionRepository.findByOptionalStatus(status).stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    /** On-demand suggestion for an order (HTTP {@code POST /orders/{id}/suggest}). */
    @Transactional
    public SuggestionResponse suggestForOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", orderId));
        return SuggestionResponse.from(createSuggestion(order, null));
    }

    /**
     * Run the active routing strategy for an order and persist the top recommendation as a
     * PENDING suggestion. Moves the order to REASSIGNMENT_PENDING and supersedes any
     * existing PENDING suggestion (one active suggestion per order).
     *
     * <p>{@code recovery} is {@code null} for an on-demand (INITIAL) suggestion and non-null
     * for an agent-offline re-plan (AGENT_OFFLINE) — the T-4 loop supplies the offline agent
     * and stranded-order count so the AI strategy can reason in recovery mode.
     */
    public ReassignmentSuggestion createSuggestion(Order order, RoutingContext.Recovery recovery) {
        order.markReassignmentPending();

        RoutingStrategy strategy = routingResolver.active();
        RoutingContext context = new RoutingContext(order, availableCandidates(order), recovery);
        List<RoutingRecommendation> recommendations = strategy.recommend(context);
        if (recommendations.isEmpty()) {
            throw new BusinessRuleException(
                    "No available agent to reassign order " + order.getId());
        }
        RoutingRecommendation top = recommendations.get(0);

        supersedeExistingPending(order);

        TriggerReason triggerReason = recovery != null ? TriggerReason.AGENT_OFFLINE : TriggerReason.INITIAL;
        ReassignmentSuggestion suggestion = new ReassignmentSuggestion(
                order, top.agent(), top.confidence(), top.reasoning(), triggerReason, strategy.name());
        return suggestionRepository.save(suggestion);
    }

    @Transactional
    public SuggestionResponse updateStatus(Long id, SuggestionStatus newStatus) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Suggestion", id));

        switch (newStatus) {
            case ACCEPTED -> accept(suggestion);
            case REJECTED -> suggestion.reject();
            default -> throw new BusinessRuleException(
                    "Suggestion status can only be set to ACCEPTED or REJECTED, got " + newStatus);
        }

        return SuggestionResponse.from(suggestion);
    }

    /** Hand the order to the recommended agent and rebalance load counts. */
    private void accept(ReassignmentSuggestion suggestion) {
        Order order = suggestion.getOrder();
        Agent newAgent = suggestion.getRecommendedAgent();
        Agent previousAgent = order.getAssignedAgent();

        order.reassignTo(newAgent);
        if (previousAgent != null) {
            previousAgent.decrementLoad();
        }
        newAgent.incrementLoad();

        suggestion.accept();

        // Backstop for the one-PENDING-per-order invariant: close any sibling PENDING
        // suggestions so accepting one clears the order from the ops queue cleanly.
        suggestionRepository.findByOrderAndStatus(order, SuggestionStatus.PENDING).stream()
                .filter(other -> !other.getId().equals(suggestion.getId()))
                .forEach(ReassignmentSuggestion::reject);
    }

    /** Available agents excluding the order's current holder (recommending it is pointless). */
    private List<Agent> availableCandidates(Order order) {
        String currentAgentId = order.getAssignedAgent() != null ? order.getAssignedAgent().getId() : null;
        return agentRepository.findByStatus(AgentStatus.AVAILABLE).stream()
                .filter(agent -> !agent.getId().equals(currentAgentId))
                .toList();
    }

    private void supersedeExistingPending(Order order) {
        suggestionRepository.findByOrderAndStatus(order, SuggestionStatus.PENDING)
                .forEach(ReassignmentSuggestion::reject);
    }
}
