package com.ek.reconciliation.fees;

import java.math.BigDecimal;

public record ExpectedSettlement(
        BigDecimal settledAmount,
        BigDecimal interchangeFee,
        BigDecimal processorFee
) {
    public BigDecimal totalFees() {
        return interchangeFee.add(processorFee);
    }
}
