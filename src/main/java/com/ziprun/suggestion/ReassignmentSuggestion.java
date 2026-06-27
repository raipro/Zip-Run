package com.ziprun.suggestion;

import com.ziprun.agent.Agent;
import com.ziprun.common.exception.BusinessRuleException;
import com.ziprun.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

/**
 * A proposed reassignment of one order to one agent — the artifact that threads through
 * the whole system. The routing engine (T-2/T-3) produces it; ops accepts or rejects it
 * (the human checkpoint).
 *
 * <p>System-generated identity (auto-increment) since suggestions aren't seeded. Carries
 * the AI's {@code confidence} and {@code reasoning} verbatim — the reasoning is what ops
 * actually reads, so it is stored and surfaced unmodified.
 */
@Entity
@Table(name = "reassignment_suggestion")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ReassignmentSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommended_agent_id", nullable = false)
    private Agent recommendedAgent;

    /** AI confidence in the recommendation, clamped to 0.0–1.0. */
    @Column(nullable = false)
    private double confidence;

    /** Plain-English justification, shown verbatim in the ops UI. */
    @Column(nullable = false, length = 2000)
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 16)
    private TriggerReason triggerReason;

    /** Which strategy produced this ("rule-based" / "ai") — for traceability & the UI. */
    @Column(name = "strategy_used", length = 32)
    private String strategyUsed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReassignmentSuggestion(Order order, Agent recommendedAgent, double confidence,
                                  String reasoning, TriggerReason triggerReason, String strategyUsed) {
        this.order = order;
        this.recommendedAgent = recommendedAgent;
        this.confidence = clamp(confidence);
        this.reasoning = reasoning;
        this.triggerReason = triggerReason;
        this.strategyUsed = strategyUsed;
        this.status = SuggestionStatus.PENDING;
    }

    public void accept() {
        requirePending();
        this.status = SuggestionStatus.ACCEPTED;
    }

    public void reject() {
        requirePending();
        this.status = SuggestionStatus.REJECTED;
    }

    private void requirePending() {
        if (status != SuggestionStatus.PENDING) {
            throw new BusinessRuleException(
                    "Suggestion " + id + " is already " + status + " and cannot be changed");
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
