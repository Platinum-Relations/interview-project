package com.reconciliation.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RunSummaryDto(
        Long runId,
        Instant importedAt,
        String internalFileName,
        String settlementFileName,
        int validInternalCount,
        int validSettlementCount,
        int quarantinedCount,
        BigDecimal expectedPayout,
        BigDecimal actualSettled,
        BigDecimal payoutDiscrepancy,
        BigDecimal totalFeesReported,
        List<CategorySummaryDto> categories) {
}
