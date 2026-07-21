package com.reconciliation.domain;

import java.util.Optional;

public enum CardType {
    VISA,
    MASTERCARD,
    AMEX,
    DISCOVER;

    public static Optional<CardType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (CardType type : values()) {
            if (type.name().equalsIgnoreCase(raw.trim())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
