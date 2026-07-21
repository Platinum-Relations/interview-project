package com.reconciliation.fees;

import com.reconciliation.domain.CardType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes the fees we expect from the published schedule.
 *
 * Per the exercise's fee rule, each fee is rounded to the cent (half-up)
 * independently BEFORE the expected settled amount is derived:
 * expected_settled = gross - round(interchange) - round(processor)
 */
public class FeeCalculator {

    private final FeeSchedule schedule;

    public FeeCalculator(FeeSchedule schedule) {
        this.schedule = schedule;
    }

    public ExpectedFees expectedFeesForSale(CardType cardType, BigDecimal gross) {
        BigDecimal interchange = applyRate(schedule.interchangeFor(cardType), gross);
        BigDecimal processor = applyRate(schedule.processorMarkup(), gross);
        return new ExpectedFees(interchange, processor);
    }

    /** Refunds settle at full negative gross with no fees. */
    public BigDecimal expectedRefundSettlement(BigDecimal grossAmount) {
        return grossAmount;
    }

    private BigDecimal applyRate(FeeSchedule.Rate rate, BigDecimal gross) {
        return gross.multiply(rate.percent())
                .add(rate.flat())
                .setScale(2, RoundingMode.HALF_UP);
    }

    public record ExpectedFees(BigDecimal interchange, BigDecimal processor) {

        public BigDecimal total() {
            return interchange.add(processor);
        }

        public BigDecimal expectedNet(BigDecimal gross) {
            return gross.subtract(interchange).subtract(processor);
        }
    }
}
