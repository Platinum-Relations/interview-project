package com.reconciliation.engine;

import com.reconciliation.domain.CardType;
import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.Settlement;
import com.reconciliation.domain.TransactionType;
import com.reconciliation.fees.FeeCalculator;
import com.reconciliation.fees.FeeSchedule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationEngineTest {

    private static ReconciliationEngine engine;

    @BeforeAll
    static void setUp() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("../fee_schedule.json"))) {
            engine = new ReconciliationEngine(
                    new FeeCalculator(FeeSchedule.load(reader)),
                    ReconciliationPolicy.standard());
        }
    }

    // VISA sale of 100.00: expected interchange 1.90, processor 0.35, net 97.75.
    private LedgerTransaction sale100(String txnId, String ref) {
        return new LedgerTransaction(txnId, "MERCH-1", ref, CardType.VISA, "1234",
                new BigDecimal("100.00"), "USD", TransactionType.SALE,
                Instant.parse("2026-06-01T12:00:00Z"));
    }

    private Settlement settlement(String ref, String amount, String interchange, String processor, LocalDate date) {
        return new Settlement("ARN-" + amount, ref, "MERCH-1", "1234", CardType.VISA,
                new BigDecimal(amount), new BigDecimal(interchange), new BigDecimal(processor), "USD", date);
    }

    @Test
    void matchesByMerchantRefAndClassifiesClean() {
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(settlement("ORD-1", "97.75", "1.90", "0.35", LocalDate.of(2026, 6, 3))));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().classification()).isEqualTo(Classification.CLEAN_MATCH);
    }

    @Test
    void fallsBackToMerchantCardAndExpectedNetWhenRefIsBlank() {
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(settlement(null, "97.75", "1.90", "0.35", LocalDate.of(2026, 6, 3))));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().classification()).isEqualTo(Classification.CLEAN_MATCH);
    }

    @Test
    void blankRefFallbackComparesNetNotGross() {
        // A settlement equal to the GROSS must not match; the ledger side is fee-adjusted.
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(settlement(null, "100.00", "1.90", "0.35", LocalDate.of(2026, 6, 3))));

        assertThat(items).extracting(ReconciliationItem::classification)
                .containsExactlyInAnyOrder(Classification.UNMATCHED_INTERNAL, Classification.UNMATCHED_SETTLEMENT);
    }

    @Test
    void rowsRepeatingTheNetAreADuplicateNotASplit() {
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(
                        settlement("ORD-1", "97.75", "1.90", "0.35", LocalDate.of(2026, 6, 3)),
                        settlement("ORD-1", "97.75", "1.90", "0.35", LocalDate.of(2026, 6, 4))));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().classification()).isEqualTo(Classification.DUPLICATE_SETTLEMENT);
        assertThat(items.getFirst().settlements()).hasSize(2);
    }

    @Test
    void rowsSummingToTheNetAreASplitNotADuplicate() {
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(
                        settlement("ORD-1", "50.00", "1.00", "0.20", LocalDate.of(2026, 6, 3)),
                        settlement("ORD-1", "47.75", "0.90", "0.15", LocalDate.of(2026, 6, 4))));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().classification()).isEqualTo(Classification.SPLIT_SETTLEMENT);
    }

    @Test
    void principalOffBeyondReportedFeesIsAmountMismatch() {
        // 100.00 - 1.90 - 0.35 = 97.75, but settled 95.00: not explainable by reported fees.
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(settlement("ORD-1", "95.00", "1.90", "0.35", LocalDate.of(2026, 6, 3))));

        assertThat(items.getFirst().classification()).isEqualTo(Classification.AMOUNT_MISMATCH);
    }

    @Test
    void internallyConsistentButOffScheduleFeesAreAFeeDiscrepancy() {
        // settled = 100.00 - 3.00 - 0.35 = 96.65: internally consistent, but interchange
        // should be 1.90 per the schedule. A settled-vs-gross-minus-reported-fees check passes;
        // only the schedule comparison catches it.
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(settlement("ORD-1", "96.65", "3.00", "0.35", LocalDate.of(2026, 6, 3))));

        assertThat(items.getFirst().classification()).isEqualTo(Classification.FEE_DISCREPANCY);
    }

    @Test
    void subCentDifferenceWithinToleranceIsClean() {
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(settlement("ORD-1", "97.74", "1.90", "0.35", LocalDate.of(2026, 6, 3))));

        assertThat(items.getFirst().classification()).isEqualTo(Classification.CLEAN_MATCH);
    }

    @Test
    void cleanPairOutsideTheLagWindowIsFlaggedAsWideWindow() {
        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale100("TXN-1", "ORD-1")),
                List.of(settlement("ORD-1", "97.75", "1.90", "0.35", LocalDate.of(2026, 6, 20))));

        assertThat(items.getFirst().classification()).isEqualTo(Classification.WIDE_WINDOW_TIMING);
    }

    @Test
    void refundWithNoOriginatingSaleIsAnOrphanEvenIfItSettledCleanly() {
        LedgerTransaction refund = new LedgerTransaction("TXN-R", "MERCH-1", "ORD-GHOST",
                CardType.VISA, "1234", new BigDecimal("-50.00"), "USD", TransactionType.REFUND,
                Instant.parse("2026-06-01T12:00:00Z"));

        List<ReconciliationItem> items = engine.reconcile(
                List.of(refund),
                List.of(settlement("ORD-GHOST", "-50.00", "0.00", "0.00", LocalDate.of(2026, 6, 3))));

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().classification()).isEqualTo(Classification.ORPHAN_REFUND);
        assertThat(items.getFirst().settlements()).hasSize(1);
    }

    @Test
    void refundPairsWithNegativeRowAndSaleWithPositiveRowUnderSharedRef() {
        LedgerTransaction sale = sale100("TXN-S", "ORD-1");
        LedgerTransaction refund = new LedgerTransaction("TXN-R", "MERCH-1", "ORD-1",
                CardType.VISA, "1234", new BigDecimal("-100.00"), "USD", TransactionType.REFUND,
                Instant.parse("2026-06-02T12:00:00Z"));

        List<ReconciliationItem> items = engine.reconcile(
                List.of(sale, refund),
                List.of(
                        settlement("ORD-1", "97.75", "1.90", "0.35", LocalDate.of(2026, 6, 3)),
                        settlement("ORD-1", "-100.00", "0.00", "0.00", LocalDate.of(2026, 6, 4))));

        assertThat(items).hasSize(2);
        assertThat(items).allMatch(item -> item.classification() == Classification.CLEAN_MATCH);
    }

    @Test
    void neverSettledSaleIsUnmatchedInternal() {
        List<ReconciliationItem> items = engine.reconcile(List.of(sale100("TXN-1", "ORD-1")), List.of());

        assertThat(items.getFirst().classification()).isEqualTo(Classification.UNMATCHED_INTERNAL);
    }

    @Test
    void settlementWithNoLedgerRecordIsUnmatchedSettlement() {
        List<ReconciliationItem> items = engine.reconcile(
                List.of(),
                List.of(settlement("ORD-UNKNOWN", "97.75", "1.90", "0.35", LocalDate.of(2026, 6, 3))));

        assertThat(items.getFirst().classification()).isEqualTo(Classification.UNMATCHED_SETTLEMENT);
        assertThat(items.getFirst().internal()).isNull();
    }
}
