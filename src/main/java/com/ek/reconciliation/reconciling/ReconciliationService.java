package com.ek.reconciliation.reconciling;

import com.ek.reconciliation.fees.ExpectedSettlement;
import com.ek.reconciliation.fees.FeeCalculationService;
import com.ek.reconciliation.reference.TransactionTypes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ReconciliationService {

    public static final String CLEAN_MATCH = "CLEAN_MATCH";
    public static final String UNMATCHED_INTERNAL = "UNMATCHED_INTERNAL";
    public static final String UNMATCHED_SETTLEMENT = "UNMATCHED_SETTLEMENT";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String FEE_DISCREPANCY = "FEE_DISCREPANCY";
    public static final String DUPLICATE_SETTLEMENT = "DUPLICATE_SETTLEMENT";
    public static final String ORPHAN_REFUND = "ORPHAN_REFUND";
    public static final String SPLIT_SETTLEMENT = "SPLIT_SETTLEMENT";
    public static final String WIDE_WINDOW_TIMING = "WIDE_WINDOW_TIMING";
    public static final String MALFORMED_QUARANTINED = "MALFORMED_QUARANTINED";

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final int NORMAL_SETTLEMENT_WINDOW_DAYS = 3;

    private final JdbcTemplate jdbcTemplate;
    private final FeeCalculationService feeCalculationService;

    public ReconciliationService(JdbcTemplate jdbcTemplate, FeeCalculationService feeCalculationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.feeCalculationService = feeCalculationService;
    }

    @Transactional
    public ReconciliationResultResponse reconcileImportedData() {
        clearPreviousReconciliationResults();
        long runId = createReconciliationRun();

        Map<String, ReconciliationSummaryAccumulator> summary = initializedSummary();
        List<ReconciliationBreakDetail> breaks = new ArrayList<>();

        List<InternalTransaction> internalTransactions = loadInternalTransactions();
        List<SettlementRecord> settlementRecords = loadSettlementRecords();
        Map<String, List<SettlementRecord>> settlementsByReferenceAndSign = groupSettlementsByReferenceAndSign(settlementRecords);
        Set<String> usedSettlementRefs = new HashSet<>();
        Set<String> saleMerchantRefs = saleMerchantRefs(internalTransactions);

        for (InternalTransaction transaction : internalTransactions) {
            ExpectedSettlement expectedSettlement = feeCalculationService.calculate(
                    transaction.cardType(),
                    transaction.grossAmount(),
                    transaction.transactionType()
            );

            List<SettlementRecord> candidates = matchingSettlementCandidates(
                    transaction,
                    expectedSettlement,
                    settlementsByReferenceAndSign,
                    settlementRecords,
                    usedSettlementRefs
            );

            if (candidates.isEmpty()) {
                addUnmatchedInternal(runId, summary, breaks, transaction, expectedSettlement);
                continue;
            }

            candidates.forEach(record -> usedSettlementRefs.add(record.networkRef()));

            if (isOrphanRefund(transaction, saleMerchantRefs)) {
                addOrphanRefund(runId, summary, breaks, transaction, candidates.get(0), expectedSettlement);
                continue;
            }

            if (candidates.size() > 1) {
                classifyMultiSettlementMatch(runId, summary, breaks, transaction, candidates, expectedSettlement);
                continue;
            }

            classifySingleSettlementMatch(runId, summary, breaks, transaction, candidates.get(0), expectedSettlement);
        }

        for (SettlementRecord settlementRecord : settlementRecords) {
            if (!usedSettlementRefs.contains(settlementRecord.networkRef())) {
                addUnmatchedSettlement(runId, summary, breaks, settlementRecord);
            }
        }

        addMalformedQuarantineSummary(summary);
        completeReconciliationRun(runId);

        return new ReconciliationResultResponse(
                runId,
                summary.values().stream()
                        .map(ReconciliationSummaryAccumulator::toSummaryItem)
                        .toList(),
                breaks
        );
    }

    private void classifySingleSettlementMatch(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            SettlementRecord settlementRecord,
            ExpectedSettlement expectedSettlement
    ) {
        BigDecimal reportedNet = transaction.grossAmount()
                .subtract(settlementRecord.interchangeFee())
                .subtract(settlementRecord.processorFee());

        if (!moneyEquals(settlementRecord.settledAmount(), reportedNet)) {
            addAmountMismatch(runId, summary, breaks, transaction, settlementRecord, expectedSettlement);
            return;
        }

        if (!moneyEquals(settlementRecord.interchangeFee(), expectedSettlement.interchangeFee())
                || !moneyEquals(settlementRecord.processorFee(), expectedSettlement.processorFee())) {
            addFeeDiscrepancy(runId, summary, breaks, transaction, settlementRecord, expectedSettlement);
            return;
        }

        if (!withinNormalSettlementWindow(transaction, settlementRecord)) {
            addWideWindowTiming(runId, summary, breaks, transaction, settlementRecord, expectedSettlement);
            return;
        }

        addCleanMatch(runId, summary, transaction, settlementRecord, expectedSettlement);
    }

    private void classifyMultiSettlementMatch(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            List<SettlementRecord> settlementRecords,
            ExpectedSettlement expectedSettlement
    ) {
        boolean eachSettlementRepeatsExpectedNet = settlementRecords.stream()
                .allMatch(record -> moneyEquals(record.settledAmount(), expectedSettlement.settledAmount()));

        BigDecimal actualSettledTotal = sumSettledAmount(settlementRecords);
        BigDecimal actualFeeTotal = sumReportedFees(settlementRecords);

        if (eachSettlementRepeatsExpectedNet) {
            addDuplicateSettlement(runId, summary, breaks, transaction, settlementRecords, expectedSettlement);
            return;
        }

        if (moneyEquals(actualSettledTotal, expectedSettlement.settledAmount())
                && moneyEquals(actualFeeTotal, expectedSettlement.totalFees())) {
            addSplitSettlement(runId, summary, breaks, transaction, settlementRecords, expectedSettlement);
            return;
        }

        addAmountMismatch(runId, summary, breaks, transaction, settlementRecords.get(0), expectedSettlement);
    }

    private List<SettlementRecord> matchingSettlementCandidates(
            InternalTransaction transaction,
            ExpectedSettlement expectedSettlement,
            Map<String, List<SettlementRecord>> settlementsByReferenceAndSign,
            List<SettlementRecord> settlementRecords,
            Set<String> usedSettlementRefs
    ) {
        String key = referenceSignKey(transaction.merchantRef(), expectedSign(transaction.transactionType()));
        List<SettlementRecord> referenceMatches = settlementsByReferenceAndSign.getOrDefault(key, List.of()).stream()
                .filter(record -> !usedSettlementRefs.contains(record.networkRef()))
                .toList();

        if (!referenceMatches.isEmpty()) {
            return referenceMatches;
        }

        return settlementRecords.stream()
                .filter(record -> !usedSettlementRefs.contains(record.networkRef()))
                .filter(record -> record.merchantRef() == null)
                .filter(record -> record.merchantId().equals(transaction.merchantId()))
                .filter(record -> record.cardType().equals(transaction.cardType()))
                .filter(record -> record.cardLast4().equals(transaction.cardLast4()))
                .filter(record -> record.currency().equals(transaction.currency()))
                .filter(record -> record.settlementSign().equals(expectedSign(transaction.transactionType())))
                .filter(record -> moneyEquals(record.settledAmount(), expectedSettlement.settledAmount()))
                .toList();
    }

    private void addCleanMatch(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            InternalTransaction transaction,
            SettlementRecord settlementRecord,
            ExpectedSettlement expectedSettlement
    ) {
        increment(summary, CLEAN_MATCH, expectedSettlement.settledAmount());
        insertReconciliationMatch(runId, transaction, settlementRecord, expectedSettlement, CLEAN_MATCH, "Matched cleanly");
    }

    private void addUnmatchedInternal(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            ExpectedSettlement expectedSettlement
    ) {
        String reason = "Internal transaction has no matching settlement record";
        increment(summary, UNMATCHED_INTERNAL, expectedSettlement.settledAmount());
        insertBreak(runId, UNMATCHED_INTERNAL, transaction.merchantId(), transaction.internalTxnId(), null,
                expectedSettlement.settledAmount(), expectedSettlement.settledAmount(), null, reason);
        breaks.add(new ReconciliationBreakDetail(UNMATCHED_INTERNAL, transaction.merchantId(), transaction.internalTxnId(),
                null, expectedSettlement.settledAmount(), expectedSettlement.settledAmount(), null, reason));
    }

    private void addUnmatchedSettlement(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            SettlementRecord settlementRecord
    ) {
        String reason = "Settlement record has no matching internal transaction";
        increment(summary, UNMATCHED_SETTLEMENT, settlementRecord.settledAmount());
        insertBreak(runId, UNMATCHED_SETTLEMENT, settlementRecord.merchantId(), null, settlementRecord.networkRef(),
                settlementRecord.settledAmount(), null, settlementRecord.settledAmount(), reason);
        breaks.add(new ReconciliationBreakDetail(UNMATCHED_SETTLEMENT, settlementRecord.merchantId(), null,
                settlementRecord.networkRef(), settlementRecord.settledAmount(), null, settlementRecord.settledAmount(), reason));
    }

    private void addAmountMismatch(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            SettlementRecord settlementRecord,
            ExpectedSettlement expectedSettlement
    ) {
        String reason = "Settled amount does not agree with gross less reported processor fees";
        BigDecimal delta = settlementRecord.settledAmount().subtract(expectedSettlement.settledAmount());
        increment(summary, AMOUNT_MISMATCH, delta);
        insertReconciliationMatch(runId, transaction, settlementRecord, expectedSettlement, AMOUNT_MISMATCH, reason);
        insertBreak(runId, AMOUNT_MISMATCH, transaction.merchantId(), transaction.internalTxnId(), settlementRecord.networkRef(),
                delta, expectedSettlement.settledAmount(), settlementRecord.settledAmount(), reason);
        breaks.add(new ReconciliationBreakDetail(AMOUNT_MISMATCH, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecord.networkRef(), delta, expectedSettlement.settledAmount(), settlementRecord.settledAmount(), reason));
    }

    private void addFeeDiscrepancy(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            SettlementRecord settlementRecord,
            ExpectedSettlement expectedSettlement
    ) {
        String reason = "Reported fees deviate from configured fee schedule";
        BigDecimal delta = settlementRecord.reportedFees().subtract(expectedSettlement.totalFees());
        increment(summary, FEE_DISCREPANCY, delta);
        insertReconciliationMatch(runId, transaction, settlementRecord, expectedSettlement, FEE_DISCREPANCY, reason);
        insertBreak(runId, FEE_DISCREPANCY, transaction.merchantId(), transaction.internalTxnId(), settlementRecord.networkRef(),
                delta, expectedSettlement.totalFees(), settlementRecord.reportedFees(), reason);
        breaks.add(new ReconciliationBreakDetail(FEE_DISCREPANCY, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecord.networkRef(), delta, expectedSettlement.totalFees(), settlementRecord.reportedFees(), reason));
    }

    private void addDuplicateSettlement(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            List<SettlementRecord> settlementRecords,
            ExpectedSettlement expectedSettlement
    ) {
        String reason = "Multiple settlement rows repeat the full expected net amount";
        BigDecimal duplicateAmount = sumSettledAmount(settlementRecords).subtract(expectedSettlement.settledAmount());
        increment(summary, DUPLICATE_SETTLEMENT, duplicateAmount);
        settlementRecords.forEach(record -> insertReconciliationMatch(runId, transaction, record, expectedSettlement,
                DUPLICATE_SETTLEMENT, reason));
        insertBreak(runId, DUPLICATE_SETTLEMENT, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecords.get(0).networkRef(), duplicateAmount, expectedSettlement.settledAmount(),
                sumSettledAmount(settlementRecords), reason);
        breaks.add(new ReconciliationBreakDetail(DUPLICATE_SETTLEMENT, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecords.get(0).networkRef(), duplicateAmount, expectedSettlement.settledAmount(),
                sumSettledAmount(settlementRecords), reason));
    }

    private void addSplitSettlement(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            List<SettlementRecord> settlementRecords,
            ExpectedSettlement expectedSettlement
    ) {
        String reason = "Multiple partial settlement rows sum to the expected net amount";
        increment(summary, SPLIT_SETTLEMENT, sumSettledAmount(settlementRecords));
        settlementRecords.forEach(record -> insertReconciliationMatch(runId, transaction, record, expectedSettlement,
                SPLIT_SETTLEMENT, reason));
        insertBreak(runId, SPLIT_SETTLEMENT, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecords.get(0).networkRef(), sumSettledAmount(settlementRecords),
                expectedSettlement.settledAmount(), sumSettledAmount(settlementRecords), reason);
        breaks.add(new ReconciliationBreakDetail(SPLIT_SETTLEMENT, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecords.get(0).networkRef(), sumSettledAmount(settlementRecords),
                expectedSettlement.settledAmount(), sumSettledAmount(settlementRecords), reason));
    }

    private void addOrphanRefund(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            SettlementRecord settlementRecord,
            ExpectedSettlement expectedSettlement
    ) {
        String reason = "Refund merchant_ref has no originating SALE in the internal ledger";
        increment(summary, ORPHAN_REFUND, settlementRecord.settledAmount());
        insertReconciliationMatch(runId, transaction, settlementRecord, expectedSettlement, ORPHAN_REFUND, reason);
        insertBreak(runId, ORPHAN_REFUND, transaction.merchantId(), transaction.internalTxnId(), settlementRecord.networkRef(),
                settlementRecord.settledAmount(), expectedSettlement.settledAmount(), settlementRecord.settledAmount(), reason);
        breaks.add(new ReconciliationBreakDetail(ORPHAN_REFUND, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecord.networkRef(), settlementRecord.settledAmount(), expectedSettlement.settledAmount(),
                settlementRecord.settledAmount(), reason));
    }

    private void addWideWindowTiming(
            long runId,
            Map<String, ReconciliationSummaryAccumulator> summary,
            List<ReconciliationBreakDetail> breaks,
            InternalTransaction transaction,
            SettlementRecord settlementRecord,
            ExpectedSettlement expectedSettlement
    ) {
        String reason = "Settlement matched but landed outside the normal settlement window";
        increment(summary, WIDE_WINDOW_TIMING, settlementRecord.settledAmount());
        insertReconciliationMatch(runId, transaction, settlementRecord, expectedSettlement, WIDE_WINDOW_TIMING, reason);
        insertBreak(runId, WIDE_WINDOW_TIMING, transaction.merchantId(), transaction.internalTxnId(), settlementRecord.networkRef(),
                settlementRecord.settledAmount(), expectedSettlement.settledAmount(), settlementRecord.settledAmount(), reason);
        breaks.add(new ReconciliationBreakDetail(WIDE_WINDOW_TIMING, transaction.merchantId(), transaction.internalTxnId(),
                settlementRecord.networkRef(), settlementRecord.settledAmount(), expectedSettlement.settledAmount(),
                settlementRecord.settledAmount(), reason));
    }

    private void insertReconciliationMatch(
            long runId,
            InternalTransaction transaction,
            SettlementRecord settlementRecord,
            ExpectedSettlement expectedSettlement,
            String outcome,
            String explanation
    ) {
        BigDecimal reportedTotalFees = settlementRecord.reportedFees();
        BigDecimal expectedTotalFees = expectedSettlement.totalFees();

        jdbcTemplate.update("""
                        insert into reconciliation_match (
                            run_id,
                            internal_txn_id,
                            network_ref,
                            match_type,
                            outcome,
                            expected_settled_amount,
                            expected_interchange_fee,
                            expected_processor_fee,
                            actual_settled_amount,
                            reported_interchange_fee,
                            reported_processor_fee,
                            amount_delta,
                            fee_delta,
                            explanation
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                runId,
                transaction.internalTxnId(),
                settlementRecord.networkRef(),
                settlementRecord.merchantRef() == null ? "FALLBACK_MERCHANT_CARD_NET" : "MERCHANT_REF_AND_SIGN",
                outcome,
                expectedSettlement.settledAmount(),
                expectedSettlement.interchangeFee(),
                expectedSettlement.processorFee(),
                settlementRecord.settledAmount(),
                settlementRecord.interchangeFee(),
                settlementRecord.processorFee(),
                settlementRecord.settledAmount().subtract(expectedSettlement.settledAmount()),
                reportedTotalFees.subtract(expectedTotalFees),
                explanation
        );
    }

    private void insertBreak(
            long runId,
            String category,
            String merchantId,
            String internalTxnId,
            String networkRef,
            BigDecimal amount,
            BigDecimal expectedAmount,
            BigDecimal actualAmount,
            String reason
    ) {
        jdbcTemplate.update("""
                        insert into reconciliation_break (
                            run_id,
                            category,
                            merchant_id,
                            internal_txn_id,
                            network_ref,
                            amount,
                            expected_amount,
                            actual_amount,
                            reason
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                runId,
                category,
                merchantId,
                internalTxnId,
                networkRef,
                amount,
                expectedAmount,
                actualAmount,
                reason
        );
    }

    private void addMalformedQuarantineSummary(Map<String, ReconciliationSummaryAccumulator> summary) {
        Integer malformedCount = jdbcTemplate.queryForObject("""
                        select count(*)
                        from quarantined_record qr
                        join import_batch ib on ib.id = qr.import_batch_id
                        where ib.id in (
                            select max(id)
                            from import_batch
                            where source_type in ('INTERNAL_TRANSACTIONS', 'PROCESSOR_SETTLEMENT')
                            group by source_type
                        )
                        """,
                Integer.class
        );
        summary.get(MALFORMED_QUARANTINED).count = malformedCount == null ? 0 : malformedCount;
    }

    private long createReconciliationRun() {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                            insert into reconciliation_run (
                                status,
                                amount_tolerance,
                                normal_settlement_window_days,
                                notes
                            )
                            values (?, ?, ?, ?)
                            """,
                    new String[]{"id"}
            );

            ps.setString(1, "RUNNING");
            ps.setBigDecimal(2, AMOUNT_TOLERANCE);
            ps.setInt(3, NORMAL_SETTLEMENT_WINDOW_DAYS);
            ps.setString(4, "Full reconciliation classification run");
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private void completeReconciliationRun(long runId) {
        jdbcTemplate.update("""
                        update reconciliation_run
                        set status = 'COMPLETED', completed_at = current_timestamp
                        where id = ?
                        """,
                runId
        );
    }

    private void clearPreviousReconciliationResults() {
        jdbcTemplate.update("delete from reconciliation_break");
        jdbcTemplate.update("delete from reconciliation_match");
        jdbcTemplate.update("delete from reconciliation_run");
    }

    private List<InternalTransaction> loadInternalTransactions() {
        return jdbcTemplate.query("""
                        select
                            internal_txn_id,
                            merchant_id,
                            merchant_ref,
                            card_type,
                            card_last4,
                            gross_amount,
                            currency,
                            transaction_type,
                            captured_at
                        from internal_transaction
                        """,
                (rs, rowNum) -> new InternalTransaction(
                        rs.getString("internal_txn_id"),
                        rs.getString("merchant_id"),
                        rs.getString("merchant_ref"),
                        rs.getString("card_type"),
                        rs.getString("card_last4"),
                        rs.getBigDecimal("gross_amount"),
                        rs.getString("currency"),
                        rs.getString("transaction_type"),
                        toInstant(rs.getTimestamp("captured_at"))
                )
        );
    }

    private List<SettlementRecord> loadSettlementRecords() {
        return jdbcTemplate.query("""
                        select
                            network_ref,
                            merchant_ref,
                            merchant_id,
                            card_last4,
                            card_type,
                            settled_amount,
                            interchange_fee,
                            processor_fee,
                            currency,
                            settlement_date,
                            settlement_sign
                        from settlement_record
                        """,
                (rs, rowNum) -> new SettlementRecord(
                        rs.getString("network_ref"),
                        rs.getString("merchant_ref"),
                        rs.getString("merchant_id"),
                        rs.getString("card_last4"),
                        rs.getString("card_type"),
                        rs.getBigDecimal("settled_amount"),
                        rs.getBigDecimal("interchange_fee"),
                        rs.getBigDecimal("processor_fee"),
                        rs.getString("currency"),
                        rs.getDate("settlement_date").toLocalDate(),
                        rs.getString("settlement_sign")
                )
        );
    }

    private Map<String, List<SettlementRecord>> groupSettlementsByReferenceAndSign(List<SettlementRecord> settlementRecords) {
        Map<String, List<SettlementRecord>> result = new HashMap<>();
        for (SettlementRecord settlementRecord : settlementRecords) {
            if (settlementRecord.merchantRef() != null) {
                result.computeIfAbsent(
                        referenceSignKey(settlementRecord.merchantRef(), settlementRecord.settlementSign()),
                        ignored -> new ArrayList<>()
                ).add(settlementRecord);
            }
        }
        return result;
    }

    private Set<String> saleMerchantRefs(List<InternalTransaction> internalTransactions) {
        Set<String> result = new HashSet<>();
        for (InternalTransaction transaction : internalTransactions) {
            if (TransactionTypes.SALE.name().equals(transaction.transactionType())) {
                result.add(transaction.merchantRef());
            }
        }
        return result;
    }

    private Map<String, ReconciliationSummaryAccumulator> initializedSummary() {
        Map<String, ReconciliationSummaryAccumulator> result = new LinkedHashMap<>();
        List.of(
                CLEAN_MATCH,
                UNMATCHED_INTERNAL,
                UNMATCHED_SETTLEMENT,
                AMOUNT_MISMATCH,
                FEE_DISCREPANCY,
                DUPLICATE_SETTLEMENT,
                ORPHAN_REFUND,
                SPLIT_SETTLEMENT,
                WIDE_WINDOW_TIMING,
                MALFORMED_QUARANTINED
        ).forEach(outcome -> result.put(outcome, new ReconciliationSummaryAccumulator(outcome)));
        return result;
    }

    private void increment(Map<String, ReconciliationSummaryAccumulator> summary, String outcome, BigDecimal amount) {
        summary.get(outcome).count++;
        summary.get(outcome).totalAmount = summary.get(outcome).totalAmount.add(amount);
    }

    private boolean isOrphanRefund(InternalTransaction transaction, Set<String> saleMerchantRefs) {
        return TransactionTypes.REFUND.name().equals(transaction.transactionType())
                && !saleMerchantRefs.contains(transaction.merchantRef());
    }

    private boolean withinNormalSettlementWindow(InternalTransaction transaction, SettlementRecord settlementRecord) {
        LocalDate capturedDate = transaction.capturedAt().atZone(ZoneOffset.UTC).toLocalDate();
        long days = ChronoUnit.DAYS.between(capturedDate, settlementRecord.settlementDate());
        return days >= 1 && days <= NORMAL_SETTLEMENT_WINDOW_DAYS;
    }

    private String expectedSign(String transactionType) {
        if (TransactionTypes.SALE.name().equals(transactionType)) {
            return "POSITIVE";
        }
        if (TransactionTypes.REFUND.name().equals(transactionType)) {
            return "NEGATIVE";
        }
        throw new IllegalArgumentException("Unsupported transaction type: " + transactionType);
    }

    private String referenceSignKey(String merchantRef, String sign) {
        return merchantRef + "|" + sign;
    }

    private BigDecimal sumSettledAmount(List<SettlementRecord> settlementRecords) {
        return settlementRecords.stream()
                .map(SettlementRecord::settledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumReportedFees(List<SettlementRecord> settlementRecords) {
        return settlementRecords.stream()
                .map(SettlementRecord::reportedFees)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean moneyEquals(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private static final class ReconciliationSummaryAccumulator {
        private final String outcome;
        private int count = 0;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private ReconciliationSummaryAccumulator(String outcome) {
            this.outcome = outcome;
        }

        private ReconciliationSummaryItem toSummaryItem() {
            return new ReconciliationSummaryItem(outcome, count, totalAmount);
        }
    }

    private record InternalTransaction(
            String internalTxnId,
            String merchantId,
            String merchantRef,
            String cardType,
            String cardLast4,
            BigDecimal grossAmount,
            String currency,
            String transactionType,
            Instant capturedAt
    ) {
    }

    private record SettlementRecord(
            String networkRef,
            String merchantRef,
            String merchantId,
            String cardLast4,
            String cardType,
            BigDecimal settledAmount,
            BigDecimal interchangeFee,
            BigDecimal processorFee,
            String currency,
            LocalDate settlementDate,
            String settlementSign
    ) {
        private BigDecimal reportedFees() {
            return interchangeFee.add(processorFee);
        }
    }
}
