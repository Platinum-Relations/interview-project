package com.reconciliation.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SettlementRowDto(
        String networkRef,
        String merchantRef,
        String merchantId,
        String cardType,
        String cardLast4,
        BigDecimal settledAmount,
        BigDecimal interchangeFee,
        BigDecimal processorFee,
        LocalDate settlementDate) {
}
