package com.reconciliation.engine;

/**
 * The outcome of reconciling one ledger transaction (or one stray settlement).
 * Everything except CLEAN_MATCH is a break an ops person needs to look at;
 * WIDE_WINDOW_TIMING is a matched pair flagged for its timing.
 */
public enum Classification {
    CLEAN_MATCH,
    UNMATCHED_INTERNAL,
    UNMATCHED_SETTLEMENT,
    AMOUNT_MISMATCH,
    FEE_DISCREPANCY,
    DUPLICATE_SETTLEMENT,
    ORPHAN_REFUND,
    SPLIT_SETTLEMENT,
    WIDE_WINDOW_TIMING;

    public boolean isBreak() {
        return this != CLEAN_MATCH;
    }
}
