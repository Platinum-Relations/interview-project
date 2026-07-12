package com.ek.reconciliation;

import com.ek.reconciliation.api.ImportResponse;
import com.ek.reconciliation.api.SettlementImportResponse;
import com.ek.reconciliation.importing.InternalTransactionImportService;
import com.ek.reconciliation.importing.SettlementRecordImportService;
import com.ek.reconciliation.reconciling.FeeApplicationResponse;
import com.ek.reconciliation.reconciling.FeeApplicationService;
import com.ek.reconciliation.reconciling.ReconciliationResultResponse;
import com.ek.reconciliation.reconciling.ReconciliationService;
import com.ek.reconciliation.reference.TransactionTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class for all post-ingestion and post-processing tests.
 */
@SpringBootTest(properties = {
        "reconciliation.imports.internal-transactions.directory=test",
        "reconciliation.imports.internal-transactions.filename=internal_transactions.csv",
        "reconciliation.imports.processor-settlement.directory=test",
        "reconciliation.imports.processor-settlement.filename=processor_settlement.json"
})
class SettlementReconciliationApplicationTests {
    // canned txn results
    private static final int TEST_TXN_INPUT_RECORD_CNT = 18;
    private static final int TEST_TXN_INTERNAL_ROWS_VALID = 15;
    private static final int TEST_TXN_INTERNAL_ROWS_QUARANTINED = 3;

    // canned settlement results
    private static final int TEST_SETTLEMENT_ROWS_INPUT = 19;
    private static final int TEST_SETTLEMENT_ROWS_VALID = 17;
    private static final int TEST_SETTLEMENT_ROWS_QUARANTINED = 2;
    private static final int TEST_MERCHANT_REF_SIGN_FEE_RECORDS = 15;
    private static final int TEST_RECONCILIATION_BREAK_ROWS = 8;

    // canned txn amounts
    private static final BigDecimal TEST_TXN_GROSS_SALES = new BigDecimal("6804.12");
    private static final BigDecimal TEST_TXN_GROSS_REFUNDS = new BigDecimal("-1557.02");
    private static final BigDecimal TEST_SETTLEMENT_GROSS_AMOUNT_VALID = new BigDecimal("5161.00");
    private static final BigDecimal TEST_SETTLEMENT_GROSS_FEES_VALID = new BigDecimal("151.74");

    // canned settled amounts
    private final InternalTransactionImportService internalTransactionImportService;
    private final SettlementRecordImportService settlementRecordImportService;
    private final FeeApplicationService feeApplicationService;
    private final ReconciliationService reconciliationService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** Path to the {@code internal_transactions.csv} file to use. */
    private final Path configuredInternalTransactionsPath;

    /** Path to the {@code processor_settlement.json} file to use. */
    private final Path configuredSettlementRecordsPath;

    @Autowired
    SettlementReconciliationApplicationTests(
            InternalTransactionImportService internalTransactionImportService,
            SettlementRecordImportService settlementRecordImportService,
            FeeApplicationService feeApplicationService,
            ReconciliationService reconciliationService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${reconciliation.imports.internal-transactions.directory}") String internalTransactionsDirectory,
            @Value("${reconciliation.imports.internal-transactions.filename}") String file_internal_transactions,
            @Value("${reconciliation.imports.processor-settlement.directory}") String settlementRecordsDirectory,
            @Value("${reconciliation.imports.processor-settlement.filename}") String file_internal_settlement
    ) {
        this.internalTransactionImportService = internalTransactionImportService;
        this.settlementRecordImportService = settlementRecordImportService;
        this.feeApplicationService = feeApplicationService;
        this.reconciliationService = reconciliationService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.configuredInternalTransactionsPath = Path.of(internalTransactionsDirectory, file_internal_transactions);
        this.configuredSettlementRecordsPath = Path.of(settlementRecordsDirectory, file_internal_settlement);
    }

    @Test
    void internalTransactionCountMatchesValidRowsFromConfiguredImportFile() throws Exception {
        int csvRecordCount = countConfiguredCsvRecords();

        ImportResponse response = internalTransactionImportService.populateFromDefaultCsv();

        Integer internalTransactionCount = jdbcTemplate.queryForObject(
                "select count(*) from internal_transaction",
                Integer.class
        );

        System.out.println("Internal Transaction Count [" + internalTransactionCount + "]");
        System.out.println("Valid Row Count [" + response.validRowCount() + "]");
        System.out.println("Quarantined Transaction Count [" + response.quarantinedRowCount() + "]");
        System.out.println("Total Input CSV Row Count [" + csvRecordCount + "]");

        assertThat(internalTransactionCount)
                .isEqualTo(response.validRowCount());

        assertThat(response.validRowCount() + response.quarantinedRowCount())
                .isEqualTo(csvRecordCount);

        assertThat(csvRecordCount).isEqualTo(TEST_TXN_INPUT_RECORD_CNT);

        assertThat(response.validRowCount()).isEqualTo(TEST_TXN_INTERNAL_ROWS_VALID);

        assertThat(response.quarantinedRowCount()).isEqualTo(TEST_TXN_INTERNAL_ROWS_QUARANTINED);

        assertThat(response.grossSalesAmount().setScale(2, RoundingMode.HALF_UP)).isEqualTo(TEST_TXN_GROSS_SALES);
        assertThat(response.grossRefundAmount().setScale(2, RoundingMode.HALF_UP)).isEqualTo(TEST_TXN_GROSS_REFUNDS);
    }


    @Test
    void internalTransactionGrossAmounts() throws Exception {
        //int csvRecordCount = countConfiguredCsvRecords();

        // response includes gross sales and refunds
        ImportResponse response = internalTransactionImportService.populateFromDefaultCsv();

        Integer internalTransactionCount = jdbcTemplate.queryForObject(
                "select count(*) from internal_transaction",
                Integer.class
        );

        // yes, yes i know... the PCI auditor wouldn't give me a cigarette nor blindfold for concatenation of sql stmts, but this isn't PROD code
        // for real code I would use PreparedStatements only, or at worst, wrap in OWASP library to safely parameterize
        BigDecimal internalGrossSalesAmount = jdbcTemplate.queryForObject(
                "SELECT sum(gross_amount) from internal_transaction"
                + " WHERE transaction_type = '" + TransactionTypes.SALE.name() + "'",
                BigDecimal.class
        );
        BigDecimal internalGrossRefundsAmount = jdbcTemplate.queryForObject(
                "SELECT sum(gross_amount) from internal_transaction"
                        + " WHERE transaction_type = '" + TransactionTypes.REFUND.name() + "'",
                BigDecimal.class
        );

        System.out.println("Internal Transaction Count [" + internalTransactionCount + "]");
        System.out.println("CSV Gross Sales from DB, raw [" + response.grossSalesAmount() + "]");
        System.out.println("internal_transaction Gross Sales [" + internalGrossSalesAmount + "]");

        System.out.println("CSV Gross Refunds [" + response.grossSalesAmount() + "]");
        System.out.println("internal_transaction Gross REfunds [" + internalGrossRefundsAmount + "]");

        assertThat(internalTransactionCount)
                .isEqualTo(response.validRowCount());

        // make sure lower assertions wont NPE
        Assertions.assertNotNull(internalGrossSalesAmount);
        Assertions.assertNotNull(internalGrossRefundsAmount);


        // check amounts, normalize for USD minor units... this is bad, in PROD code would
        // 1. Have separate sections for each currency
        // 2. Use Currency object to locate currency for each record, and use stipulated minor units of the currency
        assertThat(internalGrossSalesAmount.setScale(2, RoundingMode.HALF_UP)).isEqualTo(TEST_TXN_GROSS_SALES);
        assertThat(internalGrossRefundsAmount.setScale(2, RoundingMode.HALF_UP)).isEqualTo(TEST_TXN_GROSS_REFUNDS);
    }




    /**
     * Counts up onboarded transaction records after processing.
     * Still work in progress.
     *
     * @return Transaction records count
     * @see {@link SettlementReconciliationApplicationTests#configuredInternalTransactionsPath
     */
    private int countConfiguredCsvRecords() throws Exception {
        // init result
        int result = 0;

        // Read em in, ignore CSV header
        try (Reader reader = Files.newBufferedReader(configuredInternalTransactionsPath)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .get()
                    .parse(reader);

            // count em up
            for (CSVRecord ignored : records) {
                result++;
            }

            // done
            System.out.println("Found Total Input CSV Row Count to be [" + result + "]");
            return result;
        }
    }


    @Test
    void settledRecordCountMatchesValidRowsFromConfiguredReconciliationFile() throws Exception {
        int settledRecordCount = countConfiguredSettlementRecords();

        SettlementImportResponse response = settlementRecordImportService.populateFromDefaultJson();

        Integer settlementRecordCount = jdbcTemplate.queryForObject(
                "select count(*) from settlement_record",
                Integer.class
        );

        System.out.println("Settled Transaction Count [" + settlementRecordCount + "]");
        System.out.println("Valid Settlement Row Count [" + response.validRowCount() + "]");
        System.out.println("Quarantined Settlement Record Count [" + response.quarantinedRowCount() + "]");
        System.out.println("Total Input JSON Row Count [" + settledRecordCount + "]");
        System.out.println("Settlement Gross Amount [" + response.totalSettledAmount() + "]");
        System.out.println("Settlement Total Fees [" + response.totalFeesAmount() + "]");

        assertThat(settlementRecordCount)
                .isEqualTo(response.validRowCount());

        assertThat(response.validRowCount() + response.quarantinedRowCount())
                .isEqualTo(settledRecordCount);

        assertThat(settledRecordCount).isEqualTo(TEST_SETTLEMENT_ROWS_INPUT);

        assertThat(response.validRowCount()).isEqualTo(TEST_SETTLEMENT_ROWS_VALID);

        assertThat(response.quarantinedRowCount()).isEqualTo(TEST_SETTLEMENT_ROWS_QUARANTINED);

        assertThat(response.totalSettledAmount().setScale(2, RoundingMode.HALF_UP))
                .isEqualTo(TEST_SETTLEMENT_GROSS_AMOUNT_VALID);

        assertThat(response.totalFeesAmount().setScale(2, RoundingMode.HALF_UP))
                .isEqualTo(TEST_SETTLEMENT_GROSS_FEES_VALID);
    }

    @Test
    void expectedFeesAreAppliedAndRecordedForMerchantRefMatches() {
        internalTransactionImportService.populateFromDefaultCsv();
        settlementRecordImportService.populateFromDefaultJson();

        FeeApplicationResponse response = feeApplicationService.applyExpectedFeesToMerchantRefMatches();

        Integer recordedMatchCount = jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_match where run_id = ?",
                Integer.class,
                response.reconciliationRunId()
        );

        assertThat(recordedMatchCount).isEqualTo(response.recordedMatchCount());
        assertThat(response.recordedMatchCount()).isEqualTo(TEST_MERCHANT_REF_SIGN_FEE_RECORDS);

        assertRecordedFees(
                response.reconciliationRunId(),
                "TXN-000012",
                "ARN74000000000000078631",
                "229.58",
                "4.32",
                "0.75",
                "229.58",
                "4.32",
                "0.75",
                "0.00",
                "0.00"
        );

        assertRecordedFees(
                response.reconciliationRunId(),
                "TXN-000010",
                "ARN74000000000000036173",
                "741.75",
                "13.74",
                "2.32",
                "738.25",
                "13.74",
                "2.32",
                "-3.50",
                "0.00"
        );

        assertRecordedFees(
                response.reconciliationRunId(),
                "TXN-000011",
                "ARN74000000000000052411",
                "697.39",
                "12.93",
                "2.19",
                "695.14",
                "15.18",
                "2.19",
                "-2.25",
                "2.25"
        );

        assertRecordedFees(
                response.reconciliationRunId(),
                "TXN-000007",
                "ARN74000000000000058376",
                "-336.42",
                "0.00",
                "0.00",
                "-336.42",
                "0.00",
                "0.00",
                "0.00",
                "0.00"
        );
    }

    @Test
    void reconciliationSummaryMatchesKnownGoodExpectedResults() {
        internalTransactionImportService.populateFromDefaultCsv();
        settlementRecordImportService.populateFromDefaultJson();

        ReconciliationResultResponse response = reconciliationService.reconcileImportedData();

        assertThat(response.countFor(ReconciliationService.CLEAN_MATCH)).isEqualTo(8);
        assertThat(response.countFor(ReconciliationService.UNMATCHED_INTERNAL)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.UNMATCHED_SETTLEMENT)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.AMOUNT_MISMATCH)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.FEE_DISCREPANCY)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.DUPLICATE_SETTLEMENT)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.ORPHAN_REFUND)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.SPLIT_SETTLEMENT)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.WIDE_WINDOW_TIMING)).isEqualTo(1);
        assertThat(response.countFor(ReconciliationService.MALFORMED_QUARANTINED)).isEqualTo(5);

        Integer persistedBreakCount = jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_break where run_id = ?",
                Integer.class,
                response.reconciliationRunId()
        );

        assertThat(persistedBreakCount).isEqualTo(TEST_RECONCILIATION_BREAK_ROWS);
        assertThat(response.breaks()).hasSize(TEST_RECONCILIATION_BREAK_ROWS);

        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.UNMATCHED_INTERNAL);
        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.UNMATCHED_SETTLEMENT);
        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.AMOUNT_MISMATCH);
        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.FEE_DISCREPANCY);
        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.DUPLICATE_SETTLEMENT);
        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.ORPHAN_REFUND);
        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.SPLIT_SETTLEMENT);
        assertBreakCategoryPersisted(response.reconciliationRunId(), ReconciliationService.WIDE_WINDOW_TIMING);
    }


    /**
     * Counts up settlement records after processing.
     * Still work in progress.
     *
     * @return Settlement records count
     * @see {@link SettlementReconciliationApplicationTests#configuredSettlementRecordsPath
     */
    private int countConfiguredSettlementRecords() throws Exception {
        // Read em in, from JSON array
        try (Reader reader = Files.newBufferedReader(configuredSettlementRecordsPath)) {
            JsonNode records = objectMapper.readTree(reader);
            if (!records.isArray()) {
                throw new IllegalArgumentException("Configured settlement file must contain a JSON array");
            }

            System.out.println("Found Total Input JSON Row Count to be [" + records.size() + "]");
            return records.size();
        }
    }

    private void assertRecordedFees(
            long reconciliationRunId,
            String internalTxnId,
            String networkRef,
            String expectedSettledAmount,
            String expectedInterchangeFee,
            String expectedProcessorFee,
            String actualSettledAmount,
            String reportedInterchangeFee,
            String reportedProcessorFee,
            String amountDelta,
            String feeDelta
    ) {
        jdbcTemplate.queryForObject("""
                        select
                            expected_settled_amount,
                            expected_interchange_fee,
                            expected_processor_fee,
                            actual_settled_amount,
                            reported_interchange_fee,
                            reported_processor_fee,
                            amount_delta,
                            fee_delta
                        from reconciliation_match
                        where run_id = ?
                            and internal_txn_id = ?
                            and network_ref = ?
                        """,
                (rs, rowNum) -> {
                    assertThat(rs.getBigDecimal("expected_settled_amount").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(expectedSettledAmount));
                    assertThat(rs.getBigDecimal("expected_interchange_fee").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(expectedInterchangeFee));
                    assertThat(rs.getBigDecimal("expected_processor_fee").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(expectedProcessorFee));
                    assertThat(rs.getBigDecimal("actual_settled_amount").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(actualSettledAmount));
                    assertThat(rs.getBigDecimal("reported_interchange_fee").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(reportedInterchangeFee));
                    assertThat(rs.getBigDecimal("reported_processor_fee").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(reportedProcessorFee));
                    assertThat(rs.getBigDecimal("amount_delta").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(amountDelta));
                    assertThat(rs.getBigDecimal("fee_delta").setScale(2, RoundingMode.HALF_UP))
                            .isEqualTo(new BigDecimal(feeDelta));
                    return true;
                },
                reconciliationRunId,
                internalTxnId,
                networkRef
        );
    }

    private void assertBreakCategoryPersisted(long reconciliationRunId, String category) {
        Integer categoryCount = jdbcTemplate.queryForObject(
                "select count(*) from reconciliation_break where run_id = ? and category = ?",
                Integer.class,
                reconciliationRunId,
                category
        );

        assertThat(categoryCount).isEqualTo(1);
    }
}
