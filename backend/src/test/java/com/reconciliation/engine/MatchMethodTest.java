package com.reconciliation.engine;

import com.reconciliation.domain.CardType;
import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.Settlement;
import com.reconciliation.domain.TransactionType;
import com.reconciliation.fees.FeeCalculator;
import com.reconciliation.fees.FeeSchedule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchMethodTest {

    private static ReconciliationEngine engine;

    @BeforeAll
    static void setUp() throws Exception {
        try (Reader reader = Files.newBufferedReader(Path.of("../fee_schedule.json"))) {
            engine = new ReconciliationEngine(new FeeCalculator(FeeSchedule.load(reader)), ReconciliationPolicy.standard());
        }
    }

    @Test
    void recordsMerchantRefMatchMethod() {
        var sale = new LedgerTransaction("TXN-1", "MERCH-1", "ORD-1", CardType.VISA, "1234",
                new BigDecimal("100.00"), "USD", TransactionType.SALE, Instant.parse("2026-06-01T12:00:00Z"));
        var settlement = new Settlement("ARN-1", "ORD-1", "MERCH-1", "1234", CardType.VISA,
                new BigDecimal("97.75"), new BigDecimal("1.90"), new BigDecimal("0.35"), "USD", LocalDate.of(2026, 6, 3));
        var item = engine.reconcile(List.of(sale), List.of(settlement)).getFirst();
        assertThat(item.classification()).isEqualTo(Classification.CLEAN_MATCH);
        assertThat(item.matchMethod()).isEqualTo(MatchMethod.MERCHANT_REF);
    }

    @Test
    void recordsFallbackMatchMethod() {
        var sale = new LedgerTransaction("TXN-1", "MERCH-1", "ORD-1", CardType.VISA, "1234",
                new BigDecimal("100.00"), "USD", TransactionType.SALE, Instant.parse("2026-06-01T12:00:00Z"));
        var settlement = new Settlement("ARN-1", null, "MERCH-1", "1234", CardType.VISA,
                new BigDecimal("97.75"), new BigDecimal("1.90"), new BigDecimal("0.35"), "USD", LocalDate.of(2026, 6, 3));
        var item = engine.reconcile(List.of(sale), List.of(settlement)).getFirst();
        assertThat(item.classification()).isEqualTo(Classification.CLEAN_MATCH);
        assertThat(item.matchMethod()).isEqualTo(MatchMethod.MERCHANT_CARD_NET);
    }
}
