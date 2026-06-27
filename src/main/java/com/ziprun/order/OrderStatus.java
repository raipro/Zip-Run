package com.ziprun.order;

/**
 * Lifecycle of a delivery order.
 *
 * <pre>
 *   ASSIGNED ──► REASSIGNMENT_PENDING ──► REASSIGNED ──► DELIVERED
 *       │                                                    ▲
 *       └────────────────────────────────────────────────────┘
 *                 (an order can be delivered without reassignment)
 * </pre>
 *
 * Transitions are enforced in {@link Order} so illegal moves surface as 409s, not
 * silent data corruption.
 */
public enum OrderStatus {
    ASSIGNED,
    REASSIGNMENT_PENDING,
    REASSIGNED,
    DELIVERED
}
