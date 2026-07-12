package com.ek.reconciliation.reconciling;

import java.math.BigDecimal;

public record ReconciliationSummaryItem(
        String outcome,
        int count,
        BigDecimal totalAmount
) {
}
