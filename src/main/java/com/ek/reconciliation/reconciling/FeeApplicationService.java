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
import java.util.List;
import java.util.Objects;

@Service
public class FeeApplicationService {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final int NORMAL_SETTLEMENT_WINDOW_DAYS = 3;

    private final JdbcTemplate jdbcTemplate;
    private final FeeCalculationService feeCalculationService;

    public FeeApplicationService(JdbcTemplate jdbcTemplate, FeeCalculationService feeCalculationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.feeCalculationService = feeCalculationService;
    }

    @Transactional
    public FeeApplicationResponse applyExpectedFeesToMerchantRefMatches() {
        long reconciliationRunId = createReconciliationRun();
        int recordedMatchCount = 0;

        List<MatchedSettlementRecord> matchedRecords = jdbcTemplate.query("""
                        select
                            it.internal_txn_id,
                            it.card_type,
                            it.gross_amount,
                            it.transaction_type,
                            sr.network_ref,
                            sr.settled_amount,
                            sr.interchange_fee,
                            sr.processor_fee
                        from internal_transaction it
                        join settlement_record sr
                            on sr.merchant_ref = it.merchant_ref
                            and sr.settlement_sign = case
                                when it.transaction_type = ? then 'POSITIVE'
                                when it.transaction_type = ? then 'NEGATIVE'
                            end
                        """,
                ps -> {
                    ps.setString(1, TransactionTypes.SALE.name());
                    ps.setString(2, TransactionTypes.REFUND.name());
                },
                (rs, rowNum) -> new MatchedSettlementRecord(
                        rs.getString("internal_txn_id"),
                        rs.getString("card_type"),
                        rs.getBigDecimal("gross_amount"),
                        rs.getString("transaction_type"),
                        rs.getString("network_ref"),
                        rs.getBigDecimal("settled_amount"),
                        rs.getBigDecimal("interchange_fee"),
                        rs.getBigDecimal("processor_fee")
                )
        );

        for (MatchedSettlementRecord record : matchedRecords) {
            ExpectedSettlement expectedSettlement = feeCalculationService.calculate(
                    record.cardType(),
                    record.grossAmount(),
                    record.transactionType()
            );
            insertReconciliationMatch(reconciliationRunId, record, expectedSettlement);
            recordedMatchCount++;
        }

        completeReconciliationRun(reconciliationRunId);

        return new FeeApplicationResponse(reconciliationRunId, recordedMatchCount);
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
            ps.setString(4, "Applied expected fees to merchant_ref/sign matches");
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private void insertReconciliationMatch(
            long reconciliationRunId,
            MatchedSettlementRecord record,
            ExpectedSettlement expectedSettlement
    ) {
        BigDecimal expectedTotalFees = expectedSettlement.totalFees();
        BigDecimal reportedTotalFees = record.reportedInterchangeFee().add(record.reportedProcessorFee());

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
                reconciliationRunId,
                record.internalTxnId(),
                record.networkRef(),
                "MERCHANT_REF_AND_SIGN",
                "FEES_APPLIED",
                expectedSettlement.settledAmount(),
                expectedSettlement.interchangeFee(),
                expectedSettlement.processorFee(),
                record.actualSettledAmount(),
                record.reportedInterchangeFee(),
                record.reportedProcessorFee(),
                record.actualSettledAmount().subtract(expectedSettlement.settledAmount()),
                reportedTotalFees.subtract(expectedTotalFees),
                "Expected fees calculated from configured fee schedule"
        );
    }

    private void completeReconciliationRun(long reconciliationRunId) {
        jdbcTemplate.update("""
                        update reconciliation_run
                        set status = 'COMPLETED', completed_at = current_timestamp
                        where id = ?
                        """,
                reconciliationRunId
        );
    }

    private record MatchedSettlementRecord(
            String internalTxnId,
            String cardType,
            BigDecimal grossAmount,
            String transactionType,
            String networkRef,
            BigDecimal actualSettledAmount,
            BigDecimal reportedInterchangeFee,
            BigDecimal reportedProcessorFee
    ) {
    }
}
