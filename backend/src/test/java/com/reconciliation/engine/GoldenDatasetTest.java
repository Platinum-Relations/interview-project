package com.reconciliation.engine;

import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.Settlement;
import com.reconciliation.fees.FeeCalculator;
import com.reconciliation.fees.FeeSchedule;
import com.reconciliation.ingest.LedgerCsvParser;
import com.reconciliation.ingest.ParseResult;
import com.reconciliation.ingest.SettlementJsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test: runs the full pipeline against the hand-verified test/ dataset and
 * asserts every count and money check published in test/EXPECTED.md.
 */
class GoldenDatasetTest {

    private static ParseResult<LedgerTransaction> ledger;
    private static ParseResult<Settlement> settlements;
    private static List<ReconciliationItem> items;

    @BeforeAll
    static void runPipeline() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("../test/internal_transactions.csv"))) {
            ledger = new LedgerCsvParser().parse(reader);
        }
        try (Reader reader = Files.newBufferedReader(Path.of("../test/processor_settlement.json"))) {
            settlements = new SettlementJsonParser().parse(reader);
        }
        FeeCalculator feeCalculator;
        try (Reader reader = Files.newBufferedReader(Path.of("../fee_schedule.json"))) {
            feeCalculator = new FeeCalculator(FeeSchedule.load(reader));
        }
        ReconciliationEngine engine = new ReconciliationEngine(feeCalculator, ReconciliationPolicy.standard());
        items = engine.reconcile(ledger.valid(), settlements.valid());
    }

    private Map<Classification, Long> countsByClassification() {
        return items.stream().collect(Collectors.groupingBy(ReconciliationItem::classification, Collectors.counting()));
    }

    @Test
    void quarantinesExactlyFiveMalformedRows() {
        assertThat(ledger.quarantined()).hasSize(3);
        assertThat(settlements.quarantined()).hasSize(2);
    }

    @Test
    void reconciliationSummaryMatchesExpectedTable() {
        Map<Classification, Long> counts = countsByClassification();

        assertThat(counts.getOrDefault(Classification.CLEAN_MATCH, 0L)).as("cleanly matched").isEqualTo(8);
        assertThat(counts.getOrDefault(Classification.UNMATCHED_INTERNAL, 0L)).as("unmatched internal").isEqualTo(1);
        assertThat(counts.getOrDefault(Classification.UNMATCHED_SETTLEMENT, 0L)).as("unmatched settlement").isEqualTo(1);
        assertThat(counts.getOrDefault(Classification.AMOUNT_MISMATCH, 0L)).as("amount mismatch").isEqualTo(1);
        assertThat(counts.getOrDefault(Classification.FEE_DISCREPANCY, 0L)).as("fee discrepancy").isEqualTo(1);
        assertThat(counts.getOrDefault(Classification.DUPLICATE_SETTLEMENT, 0L)).as("duplicate settlement").isEqualTo(1);
        assertThat(counts.getOrDefault(Classification.ORPHAN_REFUND, 0L)).as("orphan refund").isEqualTo(1);
        assertThat(counts.getOrDefault(Classification.SPLIT_SETTLEMENT, 0L)).as("split settlement").isEqualTo(1);
        assertThat(counts.getOrDefault(Classification.WIDE_WINDOW_TIMING, 0L)).as("wide-window timing").isEqualTo(1);
    }

    @Test
    void cleanMatchesAreSixSalesAndTwoRefunds() {
        List<ReconciliationItem> clean = items.stream()
                .filter(item -> item.classification() == Classification.CLEAN_MATCH)
                .toList();
        long sales = clean.stream().filter(item -> item.internal().isSale()).count();
        long refunds = clean.stream().filter(item -> item.internal().isRefund()).count();

        assertThat(sales).isEqualTo(6);
        assertThat(refunds).isEqualTo(2);
    }

    @Test
    void duplicateHasTwoRowsAndSplitHasTwoRows() {
        ReconciliationItem duplicate = single(Classification.DUPLICATE_SETTLEMENT);
        ReconciliationItem split = single(Classification.SPLIT_SETTLEMENT);

        assertThat(duplicate.settlements()).hasSize(2);
        assertThat(split.settlements()).hasSize(2);
    }

    @Test
    void totalGrossOfValidSalesMatches() {
        BigDecimal totalGross = ledger.valid().stream()
                .filter(LedgerTransaction::isSale)
                .map(LedgerTransaction::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalGross).isEqualByComparingTo("6804.12");
    }

    @Test
    void totalRefundGrossMatches() {
        BigDecimal refundGross = ledger.valid().stream()
                .filter(LedgerTransaction::isRefund)
                .map(LedgerTransaction::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(refundGross).isEqualByComparingTo("-1557.02");
    }

    @Test
    void totalSettledMatches() {
        BigDecimal totalSettled = settlements.valid().stream()
                .map(Settlement::settledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalSettled).isEqualByComparingTo("5161.00");
    }

    @Test
    void totalFeesDeductedMatch() {
        BigDecimal totalFees = settlements.valid().stream()
                .map(s -> s.interchangeFee().add(s.processorFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalFees).isEqualByComparingTo("151.74");
    }

    private ReconciliationItem single(Classification classification) {
        List<ReconciliationItem> matching = items.stream()
                .filter(item -> item.classification() == classification)
                .toList();
        assertThat(matching).hasSize(1);
        return matching.getFirst();
    }
}
