package com.ek.reconciliation.api;

public record ImportResponse(
        long importBatchId,
        String sourceType,
        String sourcePath,
        int validRowCount,
        int quarantinedRowCount
) {
}
