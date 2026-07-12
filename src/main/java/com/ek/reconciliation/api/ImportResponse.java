package com.ek.reconciliation.api;

import java.math.BigDecimal;

public record ImportResponse(
        long importBatchId,
        String sourceType,
        String sourcePath,
        int validRowCount,
        int quarantinedRowCount,
        BigDecimal grossSalesAmount,
        BigDecimal grossRefundAmount
) {
}
