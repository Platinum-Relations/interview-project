package com.reconciliation.api.dto;

import java.math.BigDecimal;

public record MerchantRollupDto(
        String merchantId,
        int itemCount,
        int breakCount,
        BigDecimal totalGross,
        BigDecimal totalSettled) {
}
