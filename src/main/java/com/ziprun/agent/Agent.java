package com.ziprun.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * A delivery agent. Identity is a natural key ({@code AGT-001}) so it lines up with the
 * seed script and, crucially, with the identifiers the LLM echoes back in T-3.
 *
 * <p>Rich domain model: availability changes and load adjustments go through the methods
 * here, not setters, so the {@code activeOrderCount} invariant stays consistent.
 *
 * <p><b>Sprint-2 seams:</b> {@code currentZone} and {@code maxCapacity} are nullable
 * placeholders for zone-affinity routing and capacity limits. They cost nothing now and
 * save a migration later (see ADR-5 extensibility).
 */
@Entity
@Table(name = "agents")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Agent {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "active_order_count", nullable = false)
    private int activeOrderCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentStatus status;

    // ── Sprint-2 placeholders (nullable, unused today) ───────────────────────────
    /** Zone the agent is currently operating in — sprint-2 ZoneAffinityStrategy. */
    @Column(name = "current_zone")
    private String currentZone;

    /** Max concurrent orders the agent can carry — sprint-2 capacity constraints. */
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    public Agent(String id, String name, int activeOrderCount, AgentStatus status) {
        this.id = id;
        this.name = name;
        this.activeOrderCount = activeOrderCount;
        this.status = status;
    }

    /** True when the agent can take on new work — used by the routing engine in T-2. */
    public boolean isAvailable() {
        return status == AgentStatus.AVAILABLE;
    }

    /** Change availability. All transitions are legal; OFFLINE is what fires the loop (T-4). */
    public void changeStatus(AgentStatus newStatus) {
        this.status = newStatus;
    }

    public void incrementLoad() {
        this.activeOrderCount++;
    }

    public void decrementLoad() {
        if (this.activeOrderCount > 0) {
            this.activeOrderCount--;
        }
    }
}
