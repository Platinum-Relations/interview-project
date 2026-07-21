package com.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A validated row from the processor's settlement file (processor_settlement.json).
 * merchantRef is normalized to null when blank - the processor does not always echo it.
 */
public record Settlement(
        String networkRef,
        String merchantRef,
        String merchantId,
        String cardLast4,
        CardType cardType,
        BigDecimal settledAmount,
        BigDecimal interchangeFee,
        BigDecimal processorFee,
        String currency,
        LocalDate settlementDate) {

    public boolean hasMerchantRef() {
        return merchantRef != null;
    }

    public boolean isRefundSettlement() {
        return settledAmount.signum() < 0;
    }
}
