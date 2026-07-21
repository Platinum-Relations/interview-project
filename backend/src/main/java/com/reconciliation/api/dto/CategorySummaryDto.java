package com.reconciliation.api.dto;

import java.math.BigDecimal;

public record CategorySummaryDto(String classification, int count, BigDecimal totalAmount) {
}
