package com.ek.reconciliation.reconciling;

import java.math.BigDecimal;

public record ReconciliationBreakDetail(
        String category,
        String merchantId,
        String internalTxnId,
        String networkRef,
        BigDecimal amount,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        String reason
) {
}
