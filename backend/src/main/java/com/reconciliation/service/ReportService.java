package com.reconciliation.service;

import com.reconciliation.api.dto.BreakItemDto;
import com.reconciliation.api.dto.CategorySummaryDto;
import com.reconciliation.api.dto.LedgerSourceRowDto;
import com.reconciliation.api.dto.MerchantRollupDto;
import com.reconciliation.api.dto.QuarantineDto;
import com.reconciliation.api.dto.RunSummaryDto;
import com.reconciliation.api.dto.SettlementRowDto;
import com.reconciliation.api.dto.SettlementSourceRowDto;
import com.reconciliation.engine.Classification;
import com.reconciliation.engine.MatchMethod;
import com.reconciliation.persistence.ImportRunEntity;
import com.reconciliation.persistence.ImportRunRepository;
import com.reconciliation.persistence.QuarantinedRowRepository;
import com.reconciliation.persistence.ReconciliationItemEntity;
import com.reconciliation.persistence.ReconciliationItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the reporting views (summary, breaks, per-merchant rollup) from persisted runs. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ImportRunRepository runRepository;
    private final ReconciliationItemRepository itemRepository;
    private final QuarantinedRowRepository quarantineRepository;

    public ReportService(
            ImportRunRepository runRepository,
            ReconciliationItemRepository itemRepository,
            QuarantinedRowRepository quarantineRepository) {
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.quarantineRepository = quarantineRepository;
    }

    public RunSummaryDto summary(ImportRunEntity run) {
        List<ReconciliationItemEntity> items = itemRepository.findByRunIdOrderById(run.getId());

        Map<Classification, List<ReconciliationItemEntity>> byClassification = new LinkedHashMap<>();
        for (Classification classification : Classification.values()) {
            byClassification.put(classification, new ArrayList<>());
        }
        items.forEach(item -> byClassification.get(item.getClassification()).add(item));

        List<CategorySummaryDto> categories = byClassification.entrySet().stream()
                .map(entry -> new CategorySummaryDto(
                        entry.getKey().name(),
                        entry.getValue().size(),
                        entry.getValue().stream().map(this::itemImpactAmount).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();

        return new RunSummaryDto(
                run.getId(),
                run.getImportedAt(),
                run.getInternalFileName(),
                run.getSettlementFileName(),
                run.getValidInternalCount(),
                run.getValidSettlementCount(),
                run.getQuarantinedCount(),
                run.getExpectedPayout(),
                run.getActualSettled(),
                run.getActualSettled().subtract(run.getExpectedPayout()),
                run.getTotalFeesReported(),
                categories);
    }

    /**
     * The dollar amount a category line represents: the settled money attributed to
     * the item where settlement rows exist, otherwise the ledger side we're missing.
     */
    private BigDecimal itemImpactAmount(ReconciliationItemEntity item) {
        if (!item.getSettlementRows().isEmpty()) {
            return item.getSettlementRows().stream()
                    .map(row -> row.getSettledAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return item.getGrossAmount() != null ? item.getGrossAmount() : BigDecimal.ZERO;
    }

    public List<BreakItemDto> breaks(Long runId, Classification classification) {
        List<ReconciliationItemEntity> items = classification != null
                ? itemRepository.findByRunIdAndClassificationOrderById(runId, classification)
                : itemRepository.findByRunIdOrderById(runId);
        return items.stream()
                .filter(item -> item.getClassification().isBreak())
                .map(this::toBreakDto)
                .toList();
    }

    public List<MerchantRollupDto> merchantRollup(Long runId) {
        Map<String, List<ReconciliationItemEntity>> byMerchant = new LinkedHashMap<>();
        for (ReconciliationItemEntity item : itemRepository.findByRunIdOrderById(runId)) {
            byMerchant.computeIfAbsent(item.getMerchantId(), id -> new ArrayList<>()).add(item);
        }
        return byMerchant.entrySet().stream()
                .map(entry -> {
                    List<ReconciliationItemEntity> items = entry.getValue();
                    long breaks = items.stream().filter(i -> i.getClassification().isBreak()).count();
                    BigDecimal settled = items.stream()
                            .flatMap(i -> i.getSettlementRows().stream())
                            .map(row -> row.getSettledAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal gross = items.stream()
                            .map(ReconciliationItemEntity::getGrossAmount)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new MerchantRollupDto(
                            entry.getKey(), items.size(), (int) breaks, gross, settled);
                })
                .sorted(Comparator.comparing(MerchantRollupDto::merchantId))
                .toList();
    }

    public List<QuarantineDto> quarantine(Long runId) {
        return quarantineRepository.findByRunIdOrderById(runId).stream()
                .map(row -> new QuarantineDto(row.getSource(), row.getRowIdentifier(), row.getRawContent(), row.getReasons()))
                .toList();
    }

    /**
     * Valid ledger rows for a run, column names matching internal_transactions.csv.
     * Includes match method and paired settlement side(s) for expand-in-place UI.
     */
    public List<LedgerSourceRowDto> ledgerSourceRows(Long runId) {
        return itemRepository.findByRunIdOrderById(runId).stream()
                .filter(item -> item.getInternalTxnId() != null)
                .map(this::toLedgerSourceRow)
                .toList();
    }

    /**
     * Valid settlement rows for a run, field names matching processor_settlement.json.
     * Includes match method and paired ledger side for expand-in-place UI.
     */
    public List<SettlementSourceRowDto> settlementSourceRows(Long runId) {
        List<SettlementSourceRowDto> rows = new ArrayList<>();
        for (ReconciliationItemEntity item : itemRepository.findByRunIdOrderById(runId)) {
            LedgerSourceRowDto pairedLedger = item.getInternalTxnId() != null
                    ? toLedgerSourceRowWithoutPairs(item)
                    : null;
            for (var settlement : item.getSettlementRows()) {
                rows.add(toSettlementSourceRow(item, settlement, pairedLedger));
            }
        }
        return rows;
    }

    private LedgerSourceRowDto toLedgerSourceRow(ReconciliationItemEntity item) {
        MatchMethod method = resolveMatchMethod(item);
        List<SettlementSourceRowDto> pairs = item.getSettlementRows().stream()
                .map(settlement -> toSettlementSourceRow(item, settlement, null))
                .toList();
        return new LedgerSourceRowDto(
                item.getInternalTxnId(),
                item.getMerchantId(),
                item.getMerchantRef(),
                item.getCardType(),
                item.getCardLast4(),
                item.getGrossAmount(),
                "USD",
                item.getTransactionType(),
                item.getCapturedAt() != null ? item.getCapturedAt().toString() : null,
                item.getClassification().name(),
                method.name(),
                method.label(),
                item.getReason(),
                pairs);
    }

    private LedgerSourceRowDto toLedgerSourceRowWithoutPairs(ReconciliationItemEntity item) {
        MatchMethod method = resolveMatchMethod(item);
        return new LedgerSourceRowDto(
                item.getInternalTxnId(),
                item.getMerchantId(),
                item.getMerchantRef(),
                item.getCardType(),
                item.getCardLast4(),
                item.getGrossAmount(),
                "USD",
                item.getTransactionType(),
                item.getCapturedAt() != null ? item.getCapturedAt().toString() : null,
                item.getClassification().name(),
                method.name(),
                method.label(),
                item.getReason(),
                List.of());
    }

    private SettlementSourceRowDto toSettlementSourceRow(
            ReconciliationItemEntity item,
            com.reconciliation.persistence.SettlementRowEntity settlement,
            LedgerSourceRowDto pairedLedger) {
        MatchMethod method = resolveMatchMethod(item);
        return new SettlementSourceRowDto(
                settlement.getNetworkRef(),
                settlement.getMerchantRef(),
                settlement.getMerchantId(),
                settlement.getCardLast4(),
                settlement.getCardType(),
                settlement.getSettledAmount(),
                settlement.getInterchangeFee(),
                settlement.getProcessorFee(),
                "USD",
                settlement.getSettlementDate() != null ? settlement.getSettlementDate().toString() : null,
                item.getClassification().name(),
                method.name(),
                method.label(),
                item.getReason(),
                pairedLedger);
    }

    /**
     * Prefer the persisted match method. For runs imported before that column was
     * populated, reconstruct it: blank settlement refs mean the fallback rule.
     */
    private MatchMethod resolveMatchMethod(ReconciliationItemEntity item) {
        if (item.getMatchMethod() != null) {
            return item.getMatchMethod();
        }
        if (item.getSettlementRows().isEmpty() || item.getInternalTxnId() == null) {
            return MatchMethod.UNMATCHED;
        }
        boolean anyBlankRef = item.getSettlementRows().stream()
                .anyMatch(row -> row.getMerchantRef() == null || row.getMerchantRef().isBlank());
        return anyBlankRef ? MatchMethod.MERCHANT_CARD_NET : MatchMethod.MERCHANT_REF;
    }

    public ImportRunEntity requireRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new RunNotFoundException(runId));
    }

    public java.util.Optional<ImportRunEntity> latestRun() {
        return runRepository.findTopByOrderByImportedAtDesc();
    }

    public List<ImportRunEntity> allRuns() {
        return runRepository.findAll(org.springframework.data.domain.Sort.by("importedAt").descending());
    }

    private BreakItemDto toBreakDto(ReconciliationItemEntity item) {
        List<SettlementRowDto> rows = item.getSettlementRows().stream()
                .map(row -> new SettlementRowDto(
                        row.getNetworkRef(),
                        row.getMerchantRef(),
                        row.getMerchantId(),
                        row.getCardType(),
                        row.getCardLast4(),
                        row.getSettledAmount(),
                        row.getInterchangeFee(),
                        row.getProcessorFee(),
                        row.getSettlementDate()))
                .toList();
        return new BreakItemDto(
                item.getId(),
                item.getClassification().name(),
                item.getReason(),
                item.getMerchantId(),
                item.getInternalTxnId(),
                item.getMerchantRef(),
                item.getCardType(),
                item.getCardLast4(),
                item.getGrossAmount(),
                item.getTransactionType(),
                item.getCapturedAt(),
                rows);
    }

    public static class RunNotFoundException extends RuntimeException {
        public RunNotFoundException(Long runId) {
            super("No import run with id " + runId);
        }
    }
}
