package com.ek.reconciliation.reconciling;

import java.math.BigDecimal;
import java.util.List;

public record ReconciliationResultResponse(
        long reconciliationRunId,
        List<ReconciliationSummaryItem> summary,
        List<ReconciliationBreakDetail> breaks
) {
    public int countFor(String outcome) {
        return summary.stream()
                .filter(item -> item.outcome().equals(outcome))
                .map(ReconciliationSummaryItem::count)
                .findFirst()
                .orElse(0);
    }

    public BigDecimal totalAmountFor(String outcome) {
        return summary.stream()
                .filter(item -> item.outcome().equals(outcome))
                .map(ReconciliationSummaryItem::totalAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
