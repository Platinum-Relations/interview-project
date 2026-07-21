package com.reconciliation.engine;

import java.math.BigDecimal;

/**
 * The judgment calls the exercise leaves open, made explicit and injectable:
 *
 * - amountTolerance: +/- $0.01 absolute. Reconstructing an expected net a slightly
 *   different way than the source can differ by a cent from per-fee rounding;
 *   anything beyond a cent is a real break, anything within is noise.
 * - settlement lag window: T+1..T+3 days. Pairs outside it still match (the money
 *   did arrive) but are flagged as WIDE_WINDOW_TIMING so ops can see the latency.
 */
public record ReconciliationPolicy(
        BigDecimal amountTolerance,
        int minSettlementLagDays,
        int maxSettlementLagDays) {

    public static ReconciliationPolicy standard() {
        return new ReconciliationPolicy(new BigDecimal("0.01"), 1, 3);
    }

    public boolean withinTolerance(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(amountTolerance) <= 0;
    }
}
