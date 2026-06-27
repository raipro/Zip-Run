package com.ziprun.suggestion;

import com.ziprun.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SuggestionRepository extends JpaRepository<ReassignmentSuggestion, Long> {

    /**
     * List suggestions (optionally filtered by status), eagerly joining order +
     * recommended agent so the UI list renders without an N+1 per row.
     */
    @Query("""
            select s from ReassignmentSuggestion s
            join fetch s.order
            join fetch s.recommendedAgent
            where (:status is null or s.status = :status)
            order by s.createdAt desc
            """)
    List<ReassignmentSuggestion> findByOptionalStatus(SuggestionStatus status);

    /**
     * Open suggestions for an order — used to enforce the one-PENDING-per-order invariant:
     * creating a new suggestion supersedes (rejects) any existing PENDING one.
     */
    List<ReassignmentSuggestion> findByOrderAndStatus(Order order, SuggestionStatus status);

    /**
     * Idempotency guard for the T-4 loop: is there already an open AGENT_OFFLINE
     * suggestion for this order? If so, the loop skips creating a duplicate.
     */
    boolean existsByOrderAndTriggerReasonAndStatus(Order order, TriggerReason triggerReason,
                                                   SuggestionStatus status);
}
