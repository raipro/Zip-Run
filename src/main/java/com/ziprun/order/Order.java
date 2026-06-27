package com.ziprun.order;

import com.ziprun.agent.Agent;
import com.ziprun.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

/**
 * A delivery order, pre-assigned to an agent at the start of the shift (the "morning
 * manual assignment"). Identity is a natural key ({@code ORD-001}) to match the seed.
 *
 * <p>Rich domain model: the status machine
 * {@code ASSIGNED → REASSIGNMENT_PENDING → REASSIGNED → DELIVERED} is enforced through
 * the transition methods below — illegal moves throw {@link BusinessRuleException} (409).
 *
 * <p><b>Sprint-2 seams:</b> {@code pickupZone}, {@code dropoffZone}, {@code weightClass},
 * and {@code slaDeadline} are nullable placeholders for zone-aware and SLA-driven routing.
 * The {@code slaDeadline} in particular is the hook the sprint-3 proactive loop fires on.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Order {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private String description;

    /** The agent currently responsible for the order. Lazy — never touched on list reads. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private Agent assignedAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Sprint-2 placeholders (nullable, unused today) ───────────────────────────
    @Column(name = "pickup_zone")
    private String pickupZone;

    @Column(name = "dropoff_zone")
    private String dropoffZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_class", length = 8)
    private WeightClass weightClass;

    /** SLA breach time — the sprint-3 proactive re-plan trigger plugs in here. */
    @Column(name = "sla_deadline")
    private Instant slaDeadline;

    /**
     * Generate a natural-key id in the seed's {@code ORD-XXXXXXXX} format, for orders
     * created via the API without a caller-supplied id.
     */
    public static String generateId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public Order(String id, String description, Agent assignedAgent) {
        this.id = id;
        this.description = description;
        this.assignedAgent = assignedAgent;
        this.status = OrderStatus.ASSIGNED;
    }

    /**
     * Mark the order as needing reassignment — entered when its agent goes OFFLINE (T-4)
     * or when ops requests a suggestion. Idempotent if already pending.
     */
    public void markReassignmentPending() {
        if (status == OrderStatus.REASSIGNMENT_PENDING) {
            return;
        }
        if (status != OrderStatus.ASSIGNED && status != OrderStatus.REASSIGNED) {
            throw new BusinessRuleException(
                    "Order " + id + " cannot enter REASSIGNMENT_PENDING from " + status);
        }
        this.status = OrderStatus.REASSIGNMENT_PENDING;
    }

    /**
     * Apply an accepted reassignment: hand the order to the new agent and move to
     * REASSIGNED. Only valid while a reassignment is pending.
     */
    public void reassignTo(Agent newAgent) {
        if (status != OrderStatus.REASSIGNMENT_PENDING) {
            throw new BusinessRuleException(
                    "Order " + id + " cannot be reassigned from " + status);
        }
        this.assignedAgent = newAgent;
        this.status = OrderStatus.REASSIGNED;
    }

    public void markDelivered() {
        if (status == OrderStatus.DELIVERED || status == OrderStatus.REASSIGNMENT_PENDING) {
            throw new BusinessRuleException(
                    "Order " + id + " cannot be delivered from " + status);
        }
        this.status = OrderStatus.DELIVERED;
    }
}
