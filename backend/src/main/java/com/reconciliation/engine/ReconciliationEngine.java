package com.reconciliation.engine;

import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.Settlement;
import com.reconciliation.fees.FeeCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The reconciliation engine. Pure domain logic: no Spring, no persistence, no I/O.
 *
 * Passes, in order:
 * 1. Match settlements to ledger transactions by merchant_ref + sign
 *    (a refund reuses its sale's ref, so sign separates the two rows).
 * 2. For blank-ref settlements, fall back to merchant + card + fee-adjusted
 *    expected net within tolerance.
 * 3. Classify every ledger transaction and every leftover settlement.
 *    Orphan refunds are a ledger-level defect and take precedence over
 *    whatever happened on the settlement side.
 */
public class ReconciliationEngine {

    private final FeeCalculator feeCalculator;
    private final ReconciliationPolicy policy;

    public ReconciliationEngine(FeeCalculator feeCalculator, ReconciliationPolicy policy) {
        this.feeCalculator = feeCalculator;
        this.policy = policy;
    }

    public List<ReconciliationItem> reconcile(List<LedgerTransaction> ledger, List<Settlement> settlements) {
        Set<String> saleRefs = ledger.stream()
                .filter(LedgerTransaction::isSale)
                .map(LedgerTransaction::merchantRef)
                .collect(Collectors.toSet());

        Map<String, List<Settlement>> settlementsByRef = new HashMap<>();
        for (Settlement settlement : settlements) {
            if (settlement.hasMerchantRef()) {
                settlementsByRef.computeIfAbsent(settlement.merchantRef(), ref -> new ArrayList<>()).add(settlement);
            }
        }

        Set<Settlement> consumed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<LedgerTransaction, List<Settlement>> attributions = new IdentityHashMap<>();
        Map<LedgerTransaction, MatchMethod> matchMethods = new IdentityHashMap<>();

        // Pass 1: match by merchant_ref + sign.
        for (LedgerTransaction txn : ledger) {
            List<Settlement> candidates = settlementsByRef.getOrDefault(txn.merchantRef(), List.of()).stream()
                    .filter(s -> !consumed.contains(s))
                    .filter(s -> s.isRefundSettlement() == txn.isRefund())
                    .toList();
            if (!candidates.isEmpty()) {
                candidates.forEach(consumed::add);
                attributions.put(txn, new ArrayList<>(candidates));
                matchMethods.put(txn, MatchMethod.MERCHANT_REF);
            }
        }

        // Pass 2: blank-ref settlements against still-unmatched ledger rows,
        // keyed on merchant + card, compared against the fee-adjusted expected net.
        List<Settlement> blankRefRemaining = settlements.stream()
                .filter(s -> !s.hasMerchantRef())
                .filter(s -> !consumed.contains(s))
                .toList();
        for (Settlement settlement : blankRefRemaining) {
            findFallbackMatch(settlement, ledger, attributions).ifPresent(txn -> {
                consumed.add(settlement);
                attributions.computeIfAbsent(txn, t -> new ArrayList<>()).add(settlement);
                matchMethods.putIfAbsent(txn, MatchMethod.MERCHANT_CARD_NET);
            });
        }

        // Pass 3: classify.
        List<ReconciliationItem> items = new ArrayList<>();
        for (LedgerTransaction txn : ledger) {
            List<Settlement> attributed = attributions.getOrDefault(txn, List.of());
            MatchMethod method = attributed.isEmpty()
                    ? MatchMethod.UNMATCHED
                    : matchMethods.getOrDefault(txn, MatchMethod.UNMATCHED);
            items.add(classify(txn, attributed, method, saleRefs));
        }
        for (Settlement settlement : settlements) {
            if (!consumed.contains(settlement)) {
                items.add(ReconciliationItem.settlementOnly(settlement,
                        "Settled " + settlement.settledAmount() + " (" + settlement.networkRef()
                                + ") with no corresponding ledger transaction"));
            }
        }
        return items;
    }

    private java.util.Optional<LedgerTransaction> findFallbackMatch(
            Settlement settlement,
            List<LedgerTransaction> ledger,
            Map<LedgerTransaction, List<Settlement>> attributions) {

        List<LedgerTransaction> candidates = ledger.stream()
                .filter(txn -> !attributions.containsKey(txn))
                .filter(txn -> txn.isRefund() == settlement.isRefundSettlement())
                .filter(txn -> txn.merchantId().equals(settlement.merchantId()))
                .filter(txn -> txn.cardType() == settlement.cardType())
                .filter(txn -> txn.cardLast4().equals(settlement.cardLast4()))
                .filter(txn -> policy.withinTolerance(settlement.settledAmount(), expectedSettled(txn)))
                .toList();

        // Prefer a candidate whose capture date sits inside the normal settlement window;
        // ties broken by closest capture date for determinism.
        return candidates.stream()
                .sorted(Comparator
                        .comparing((LedgerTransaction txn) -> !isWithinWindow(txn, settlement))
                        .thenComparing(txn -> Math.abs(lagDays(txn, settlement))))
                .findFirst();
    }

    private ReconciliationItem classify(
            LedgerTransaction txn,
            List<Settlement> attributed,
            MatchMethod matchMethod,
            Set<String> saleRefs) {

        boolean orphanRefund = txn.isRefund() && !saleRefs.contains(txn.merchantRef());

        if (attributed.isEmpty()) {
            if (orphanRefund) {
                return ReconciliationItem.of(txn, List.of(), Classification.ORPHAN_REFUND, MatchMethod.UNMATCHED,
                        "Refund " + txn.internalTxnId() + " references " + txn.merchantRef()
                                + " but no sale with that reference exists in the ledger (and it never settled)");
            }
            return ReconciliationItem.of(txn, List.of(), Classification.UNMATCHED_INTERNAL, MatchMethod.UNMATCHED,
                    (txn.isSale() ? "Sale" : "Refund") + " of " + txn.grossAmount()
                            + " captured " + txn.capturedAt() + " never appeared in the settlement file");
        }

        if (orphanRefund) {
            return ReconciliationItem.of(txn, attributed, Classification.ORPHAN_REFUND, matchMethod,
                    "Refund " + txn.internalTxnId() + " settled, but references " + txn.merchantRef()
                            + " and no sale with that reference exists in the ledger");
        }

        if (attributed.size() > 1) {
            return classifyMultiRow(txn, attributed, matchMethod);
        }
        return classifySingleRow(txn, attributed.getFirst(), matchMethod);
    }

    /** Multiple settlement rows for one capture: duplicate (rows repeat the net) vs split (rows sum to it). */
    private ReconciliationItem classifyMultiRow(
            LedgerTransaction txn, List<Settlement> rows, MatchMethod matchMethod) {
        BigDecimal expected = expectedSettled(txn);

        boolean everyRowRepeatsNet = rows.stream()
                .allMatch(s -> policy.withinTolerance(s.settledAmount(), expected));
        if (everyRowRepeatsNet) {
            return ReconciliationItem.of(txn, rows, Classification.DUPLICATE_SETTLEMENT, matchMethod,
                    rows.size() + " settlement rows each repeat the expected net of " + expected
                            + " - the payment settled " + rows.size() + " times and we would be double-paid");
        }

        BigDecimal sum = rows.stream().map(Settlement::settledAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (policy.withinTolerance(sum, expected)) {
            return ReconciliationItem.of(txn, rows, Classification.SPLIT_SETTLEMENT, matchMethod,
                    rows.size() + " partial settlement rows sum to the expected net of " + expected
                            + " - a split settlement, not a duplicate");
        }

        return ReconciliationItem.of(txn, rows, Classification.AMOUNT_MISMATCH, matchMethod,
                rows.size() + " settlement rows neither repeat nor sum to the expected net of " + expected
                        + " (rows total " + sum + ")");
    }

    private ReconciliationItem classifySingleRow(
            LedgerTransaction txn, Settlement settlement, MatchMethod matchMethod) {
        // A settlement can be wrong two ways; separate them by asking whether the
        // settled amount is internally consistent with the fees the processor reported.
        BigDecimal internallyConsistentNet = txn.grossAmount()
                .subtract(txn.isSale() ? settlement.interchangeFee() : BigDecimal.ZERO)
                .subtract(txn.isSale() ? settlement.processorFee() : BigDecimal.ZERO);

        if (!policy.withinTolerance(settlement.settledAmount(), internallyConsistentNet)) {
            return ReconciliationItem.of(txn, List.of(settlement), Classification.AMOUNT_MISMATCH, matchMethod,
                    "Settled " + settlement.settledAmount() + " but gross " + txn.grossAmount()
                            + " minus the processor's own reported fees comes to " + internallyConsistentNet
                            + " - the principal is off");
        }

        if (txn.isSale()) {
            FeeCalculator.ExpectedFees expected = feeCalculator.expectedFeesForSale(txn.cardType(), txn.grossAmount());
            boolean interchangeOk = policy.withinTolerance(settlement.interchangeFee(), expected.interchange());
            boolean processorOk = policy.withinTolerance(settlement.processorFee(), expected.processor());
            if (!interchangeOk || !processorOk) {
                return ReconciliationItem.of(txn, List.of(settlement), Classification.FEE_DISCREPANCY, matchMethod,
                        "Reported fees (interchange " + settlement.interchangeFee()
                                + ", processor " + settlement.processorFee()
                                + ") deviate from the published schedule (expected "
                                + expected.interchange() + " and " + expected.processor() + ")");
            }
        } else {
            boolean feesCharged = settlement.interchangeFee().signum() != 0 || settlement.processorFee().signum() != 0;
            if (feesCharged) {
                return ReconciliationItem.of(txn, List.of(settlement), Classification.FEE_DISCREPANCY, matchMethod,
                        "Refunds settle with no fees, but the processor reported interchange "
                                + settlement.interchangeFee() + " and processor fee " + settlement.processorFee());
            }
        }

        if (!isWithinWindow(txn, settlement)) {
            return ReconciliationItem.of(txn, List.of(settlement), Classification.WIDE_WINDOW_TIMING, matchMethod,
                    "Matched cleanly but settled " + lagDays(txn, settlement) + " days after capture - outside the T+"
                            + policy.minSettlementLagDays() + "..T+" + policy.maxSettlementLagDays() + " window");
        }

        return ReconciliationItem.of(txn, List.of(settlement), Classification.CLEAN_MATCH, matchMethod, "Matched cleanly");
    }

    private BigDecimal expectedSettled(LedgerTransaction txn) {
        if (txn.isRefund()) {
            return feeCalculator.expectedRefundSettlement(txn.grossAmount());
        }
        return feeCalculator.expectedFeesForSale(txn.cardType(), txn.grossAmount()).expectedNet(txn.grossAmount());
    }

    private long lagDays(LedgerTransaction txn, Settlement settlement) {
        LocalDate captured = txn.capturedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return ChronoUnit.DAYS.between(captured, settlement.settlementDate());
    }

    private boolean isWithinWindow(LedgerTransaction txn, Settlement settlement) {
        long lag = lagDays(txn, settlement);
        return lag >= policy.minSettlementLagDays() && lag <= policy.maxSettlementLagDays();
    }
}
