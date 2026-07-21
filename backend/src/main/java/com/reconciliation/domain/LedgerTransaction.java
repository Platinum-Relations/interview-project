package com.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A validated row from the internal ledger (internal_transactions.csv).
 * Instances only exist for structurally valid rows; malformed rows are
 * quarantined at parse time and never become a LedgerTransaction.
 */
public record LedgerTransaction(
        String internalTxnId,
        String merchantId,
        String merchantRef,
        CardType cardType,
        String cardLast4,
        BigDecimal grossAmount,
        String currency,
        TransactionType type,
        Instant capturedAt) {

    public boolean isSale() {
        return type == TransactionType.SALE;
    }

    public boolean isRefund() {
        return type == TransactionType.REFUND;
    }
}
