package com.ek.reconciliation.fees;

import com.ek.reconciliation.reference.TransactionTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;

@Service
public class FeeCalculationService {

    private static final int USD_MINOR_UNITS = 2;

    private final JsonNode feeSchedule;

    public FeeCalculationService(
            ObjectMapper objectMapper,
            @Value("${reconciliation.fee-schedule.path:fee_schedule.json}") String feeSchedulePath
    ) {
        try {
            this.feeSchedule = objectMapper.readTree(Path.of(feeSchedulePath).toFile());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load fee schedule from " + feeSchedulePath, ex);
        }
    }

    public ExpectedSettlement calculate(String cardType, BigDecimal grossAmount, String transactionType) {
        if (TransactionTypes.REFUND.name().equals(transactionType)) {
            return new ExpectedSettlement(grossAmount, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (!TransactionTypes.SALE.name().equals(transactionType)) {
            throw new IllegalArgumentException("Unsupported transaction type for fee calculation: " + transactionType);
        }

        JsonNode interchangeRule = feeSchedule.path("interchange").path(cardType);
        if (interchangeRule.isMissingNode()) {
            throw new IllegalArgumentException("Unsupported card type for fee calculation: " + cardType);
        }

        BigDecimal interchangeFee = calculateFee(
                grossAmount,
                requiredDecimal(interchangeRule, "percent", cardType),
                requiredDecimal(interchangeRule, "flat", cardType)
        );

        JsonNode processorMarkup = feeSchedule.path("processor_markup");
        BigDecimal processorFee = calculateFee(
                grossAmount,
                requiredDecimal(processorMarkup, "percent", "processor_markup"),
                requiredDecimal(processorMarkup, "flat", "processor_markup")
        );

        return new ExpectedSettlement(
                grossAmount.subtract(interchangeFee).subtract(processorFee),
                interchangeFee,
                processorFee
        );
    }

    private BigDecimal calculateFee(BigDecimal grossAmount, BigDecimal percent, BigDecimal flat) {
        return grossAmount.multiply(percent)
                .add(flat)
                .setScale(USD_MINOR_UNITS, RoundingMode.HALF_UP);
    }

    private BigDecimal requiredDecimal(JsonNode node, String fieldName, String ruleName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing fee schedule value " + ruleName + "." + fieldName);
        }
        return new BigDecimal(value.asText());
    }
}
