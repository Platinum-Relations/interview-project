package com.reconciliation.domain;

import java.util.Optional;

public enum TransactionType {
    SALE,
    REFUND;

    public static Optional<TransactionType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (TransactionType type : values()) {
            if (type.name().equalsIgnoreCase(raw.trim())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
