package com.ek.reconciliation.reconciling;

public record FeeApplicationResponse(
        long reconciliationRunId,
        int recordedMatchCount
) {
}
