package com.reconciliation.api.dto;

import java.time.Instant;

public record RunListItemDto(
        Long runId,
        Instant importedAt,
        String internalFileName,
        String settlementFileName,
        int validInternalCount,
        int validSettlementCount,
        int quarantinedCount) {
}
