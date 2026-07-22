package com.reconciliation.engine;

/**
 * How a ledger row and settlement row(s) were linked (or that they weren't).
 */
public enum MatchMethod {
    MERCHANT_REF,
    MERCHANT_CARD_NET,
    UNMATCHED;

    public String label() {
        return switch (this) {
            case MERCHANT_REF -> "via merchant_ref";
            case MERCHANT_CARD_NET -> "via merchant + card + net";
            case UNMATCHED -> "unmatched";
        };
    }
}
