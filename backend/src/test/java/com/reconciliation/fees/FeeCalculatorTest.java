package com.reconciliation.fees;

import com.reconciliation.domain.CardType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private static FeeCalculator calculator;

    @BeforeAll
    static void loadSchedule() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("../fee_schedule.json"))) {
            calculator = new FeeCalculator(FeeSchedule.load(reader));
        }
    }

    @Test
    void visaSaleOf100() {
        // interchange: 100 * 1.8% + 0.10 = 1.90; processor: 100 * 0.3% + 0.05 = 0.35
        FeeCalculator.ExpectedFees fees = calculator.expectedFeesForSale(CardType.VISA, new BigDecimal("100.00"));

        assertThat(fees.interchange()).isEqualByComparingTo("1.90");
        assertThat(fees.processor()).isEqualByComparingTo("0.35");
        assertThat(fees.expectedNet(new BigDecimal("100.00"))).isEqualByComparingTo("97.75");
    }

    @Test
    void eachFeeIsRoundedHalfUpBeforeDerivingNet() {
        // gross 25.00 VISA: processor = 25 * 0.003 + 0.05 = 0.125 -> must round half-up to 0.13.
        FeeCalculator.ExpectedFees fees = calculator.expectedFeesForSale(CardType.VISA, new BigDecimal("25.00"));

        assertThat(fees.processor()).isEqualByComparingTo("0.13");
        assertThat(fees.interchange()).isEqualByComparingTo("0.55");
        assertThat(fees.expectedNet(new BigDecimal("25.00"))).isEqualByComparingTo("24.32");
    }

    @Test
    void amexUsesItsOwnSchedule() {
        // interchange: 200 * 2.5% + 0.15 = 5.15; processor: 200 * 0.3% + 0.05 = 0.65
        FeeCalculator.ExpectedFees fees = calculator.expectedFeesForSale(CardType.AMEX, new BigDecimal("200.00"));

        assertThat(fees.interchange()).isEqualByComparingTo("5.15");
        assertThat(fees.processor()).isEqualByComparingTo("0.65");
    }

    @Test
    void mastercardAndDiscoverRates() {
        // MASTERCARD 150.00: 150 * 1.9% + 0.10 = 2.95; DISCOVER 150.00: 150 * 2.0% + 0.10 = 3.10
        assertThat(calculator.expectedFeesForSale(CardType.MASTERCARD, new BigDecimal("150.00")).interchange())
                .isEqualByComparingTo("2.95");
        assertThat(calculator.expectedFeesForSale(CardType.DISCOVER, new BigDecimal("150.00")).interchange())
                .isEqualByComparingTo("3.10");
    }

    @Test
    void oddCentsRoundPerFeeNotOnce() {
        // gross 234.65 VISA:
        // interchange raw = 234.65 * 0.018 + 0.10 = 4.3237 -> 4.32
        // processor raw   = 234.65 * 0.003 + 0.05 = 0.75395 -> 0.75
        // net = 234.65 - 4.32 - 0.75 = 229.58 (matches the clean test fixture row ORD-008-17602)
        FeeCalculator.ExpectedFees fees = calculator.expectedFeesForSale(CardType.VISA, new BigDecimal("234.65"));

        assertThat(fees.interchange()).isEqualByComparingTo("4.32");
        assertThat(fees.processor()).isEqualByComparingTo("0.75");
        assertThat(fees.expectedNet(new BigDecimal("234.65"))).isEqualByComparingTo("229.58");
    }

    @Test
    void refundSettlesAtFullNegativeGrossWithNoFees() {
        assertThat(calculator.expectedRefundSettlement(new BigDecimal("-336.42")))
                .isEqualByComparingTo("-336.42");
    }
}
