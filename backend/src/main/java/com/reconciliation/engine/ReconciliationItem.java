package com.reconciliation.engine;

import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.Settlement;

import java.util.List;

/**
 * One reconciled unit: a ledger transaction with the settlement rows attributed
 * to it (possibly none), or a settlement with no ledger side. The reason string
 * is written for an ops reader, not a developer.
 */
public record ReconciliationItem(
        LedgerTransaction internal,
        List<Settlement> settlements,
        Classification classification,
        String reason) {

    public static ReconciliationItem of(
            LedgerTransaction internal,
            List<Settlement> settlements,
            Classification classification,
            String reason) {
        return new ReconciliationItem(internal, List.copyOf(settlements), classification, reason);
    }

    public static ReconciliationItem settlementOnly(Settlement settlement, String reason) {
        return new ReconciliationItem(null, List.of(settlement), Classification.UNMATCHED_SETTLEMENT, reason);
    }
}
