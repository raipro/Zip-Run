package com.ziprun.suggestion.dto;

import com.ziprun.suggestion.ReassignmentSuggestion;
import com.ziprun.suggestion.SuggestionStatus;
import com.ziprun.suggestion.TriggerReason;
import lombok.Getter;

import java.time.Instant;

/**
 * API view of a reassignment suggestion — everything the ops UI renders inline:
 * the order it concerns, the recommended agent, confidence, the AI reasoning verbatim,
 * status, and the trigger reason that drives the re-plan badge.
 */
@Getter
public class SuggestionResponse {

    private final Long id;
    private final String orderId;
    private final String orderDescription;
    private final String recommendedAgentId;
    private final String recommendedAgentName;
    private final double confidence;
    private final String reasoning;
    private final SuggestionStatus status;
    private final TriggerReason triggerReason;
    private final String strategyUsed;
    private final Instant createdAt;

    public SuggestionResponse(Long id, String orderId, String orderDescription,
                              String recommendedAgentId, String recommendedAgentName,
                              double confidence, String reasoning, SuggestionStatus status,
                              TriggerReason triggerReason, String strategyUsed, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderDescription = orderDescription;
        this.recommendedAgentId = recommendedAgentId;
        this.recommendedAgentName = recommendedAgentName;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.status = status;
        this.triggerReason = triggerReason;
        this.strategyUsed = strategyUsed;
        this.createdAt = createdAt;
    }

    public static SuggestionResponse from(ReassignmentSuggestion s) {
        return new SuggestionResponse(
                s.getId(),
                s.getOrder().getId(),
                s.getOrder().getDescription(),
                s.getRecommendedAgent().getId(),
                s.getRecommendedAgent().getName(),
                s.getConfidence(),
                s.getReasoning(),
                s.getStatus(),
                s.getTriggerReason(),
                s.getStrategyUsed(),
                s.getCreatedAt());
    }
}
