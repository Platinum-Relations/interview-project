package com.reconciliation.service;

import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.Settlement;
import com.reconciliation.engine.ReconciliationEngine;
import com.reconciliation.engine.ReconciliationItem;
import com.reconciliation.fees.FeeCalculator;
import com.reconciliation.ingest.LedgerCsvParser;
import com.reconciliation.ingest.ParseResult;
import com.reconciliation.ingest.QuarantinedRow;
import com.reconciliation.ingest.SettlementJsonParser;
import com.reconciliation.persistence.ImportRunEntity;
import com.reconciliation.persistence.ImportRunRepository;
import com.reconciliation.persistence.QuarantinedRowEntity;
import com.reconciliation.persistence.QuarantinedRowRepository;
import com.reconciliation.persistence.ReconciliationItemEntity;
import com.reconciliation.persistence.ReconciliationItemRepository;
import com.reconciliation.persistence.SettlementRowEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Orchestrates one import: parse both files, reconcile, persist everything.
 * Idempotent - re-importing byte-identical files returns the existing run.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ReconciliationEngine engine;
    private final FeeCalculator feeCalculator;
    private final ImportRunRepository runRepository;
    private final ReconciliationItemRepository itemRepository;
    private final QuarantinedRowRepository quarantineRepository;

    public ImportService(
            ReconciliationEngine engine,
            FeeCalculator feeCalculator,
            ImportRunRepository runRepository,
            ReconciliationItemRepository itemRepository,
            QuarantinedRowRepository quarantineRepository) {
        this.engine = engine;
        this.feeCalculator = feeCalculator;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.quarantineRepository = quarantineRepository;
    }

    public record ImportOutcome(ImportRunEntity run, boolean alreadyImported) {
    }

    @Transactional
    public ImportOutcome importFiles(
            String internalFileName, String internalCsv,
            String settlementFileName, String settlementJson) {

        String contentHash = sha256(internalCsv + "\u0000" + settlementJson);
        var existing = runRepository.findByContentHash(contentHash);
        if (existing.isPresent()) {
            log.info("Import skipped: identical content already imported as run {}", existing.get().getId());
            return new ImportOutcome(existing.get(), true);
        }

        ParseResult<LedgerTransaction> ledger = new LedgerCsvParser().parse(new StringReader(internalCsv));
        ParseResult<Settlement> settlements = new SettlementJsonParser().parse(new StringReader(settlementJson));
        List<ReconciliationItem> items = engine.reconcile(ledger.valid(), settlements.valid());

        ImportRunEntity run = runRepository.save(new ImportRunEntity(
                Instant.now(),
                internalFileName,
                settlementFileName,
                contentHash,
                expectedPayout(ledger.valid()),
                totalSettled(settlements.valid()),
                totalReportedFees(settlements.valid()),
                ledger.valid().size(),
                settlements.valid().size(),
                ledger.quarantined().size() + settlements.quarantined().size()));

        for (ReconciliationItem item : items) {
            itemRepository.save(toEntity(run, item));
        }
        for (QuarantinedRow row : ledger.quarantined()) {
            quarantineRepository.save(toEntity(run, row));
        }
        for (QuarantinedRow row : settlements.quarantined()) {
            quarantineRepository.save(toEntity(run, row));
        }

        log.info("Imported run {}: {} ledger rows, {} settlement rows, {} quarantined, {} items",
                run.getId(), ledger.valid().size(), settlements.valid().size(),
                run.getQuarantinedCount(), items.size());
        return new ImportOutcome(run, false);
    }

    /**
     * What we expect the processor to pay out for the valid ledger rows:
     * fee-adjusted net for sales, full negative gross for refunds.
     */
    private BigDecimal expectedPayout(List<LedgerTransaction> ledger) {
        BigDecimal total = BigDecimal.ZERO;
        for (LedgerTransaction txn : ledger) {
            if (txn.isSale()) {
                total = total.add(feeCalculator
                        .expectedFeesForSale(txn.cardType(), txn.grossAmount())
                        .expectedNet(txn.grossAmount()));
            } else {
                total = total.add(feeCalculator.expectedRefundSettlement(txn.grossAmount()));
            }
        }
        return total;
    }

    private BigDecimal totalSettled(List<Settlement> settlements) {
        return settlements.stream().map(Settlement::settledAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalReportedFees(List<Settlement> settlements) {
        return settlements.stream()
                .map(s -> s.interchangeFee().add(s.processorFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ReconciliationItemEntity toEntity(ImportRunEntity run, ReconciliationItem item) {
        String merchantId = item.internal() != null
                ? item.internal().merchantId()
                : item.settlements().getFirst().merchantId();

        ReconciliationItemEntity entity = new ReconciliationItemEntity(
                run, item.classification(), item.matchMethod(), item.reason(), merchantId);

        if (item.internal() != null) {
            LedgerTransaction txn = item.internal();
            entity.setInternalSide(
                    txn.internalTxnId(), txn.merchantRef(), txn.cardType().name(), txn.cardLast4(),
                    txn.grossAmount(), txn.type().name(), txn.capturedAt());
        }
        for (Settlement settlement : item.settlements()) {
            entity.addSettlementRow(new SettlementRowEntity(
                    entity,
                    settlement.networkRef(),
                    settlement.merchantRef(),
                    settlement.merchantId(),
                    settlement.cardType() != null ? settlement.cardType().name() : null,
                    settlement.cardLast4(),
                    settlement.settledAmount(),
                    settlement.interchangeFee(),
                    settlement.processorFee(),
                    settlement.settlementDate()));
        }
        return entity;
    }

    private QuarantinedRowEntity toEntity(ImportRunEntity run, QuarantinedRow row) {
        return new QuarantinedRowEntity(
                run, row.source().name(), row.rowIdentifier(), row.rawContent(),
                String.join("; ", row.reasons()));
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
