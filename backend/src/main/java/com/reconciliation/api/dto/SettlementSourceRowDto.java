package com.reconciliation.api.dto;

import java.math.BigDecimal;

/**
 * Settlement row reshaped to match the original JSON field names, plus how it
 * was paired and the ledger side for expand-in-place in the UI.
 */
public record SettlementSourceRowDto(
        String network_ref,
        String merchant_ref,
        String merchant_id,
        String card_last4,
        String card_type,
        BigDecimal settled_amount,
        BigDecimal interchange_fee,
        BigDecimal processor_fee,
        String currency,
        String settlement_date,
        String classification,
        String match_method,
        String match_method_label,
        String reason,
        LedgerSourceRowDto paired_ledger) {
}
