package com.reconciliation.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ledger row reshaped to match the original CSV column names, plus how it was
 * paired and the settlement side(s) for expand-in-place in the UI.
 */
public record LedgerSourceRowDto(
        String internal_txn_id,
        String merchant_id,
        String merchant_ref,
        String card_type,
        String card_last4,
        BigDecimal gross_amount,
        String currency,
        String type,
        String captured_at,
        String classification,
        String match_method,
        String match_method_label,
        String reason,
        List<SettlementSourceRowDto> paired_settlements) {
}
