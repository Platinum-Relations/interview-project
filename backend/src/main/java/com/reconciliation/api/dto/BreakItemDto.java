package com.reconciliation.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One break with both sides (where they exist) and the ops-readable reason. */
public record BreakItemDto(
        Long id,
        String classification,
        String reason,
        String merchantId,
        String internalTxnId,
        String merchantRef,
        String cardType,
        String cardLast4,
        BigDecimal grossAmount,
        String transactionType,
        Instant capturedAt,
        List<SettlementRowDto> settlementRows) {
}
