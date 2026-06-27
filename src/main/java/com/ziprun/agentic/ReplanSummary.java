package com.ziprun.agentic;

/**
 * Outcome of one re-planning pass for an offline agent: how many suggestions were created,
 * how many were skipped as duplicates (idempotency), and how many orders failed outright.
 */
public record ReplanSummary(int created, int skipped, int failed) {

    static ReplanSummary empty() {
        return new ReplanSummary(0, 0, 0);
    }
}
