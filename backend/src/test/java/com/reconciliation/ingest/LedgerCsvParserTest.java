package com.reconciliation.ingest;

import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCsvParserTest {

    private final LedgerCsvParser parser = new LedgerCsvParser();

    private static final String HEADER =
            "internal_txn_id,merchant_id,merchant_ref,card_type,card_last4,gross_amount,currency,type,captured_at\n";

    @Test
    void parsesTestFixtureQuarantiningThreeMalformedRows() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("../test/internal_transactions.csv"))) {
            ParseResult<LedgerTransaction> result = parser.parse(reader);

            assertThat(result.valid()).hasSize(15);
            assertThat(result.quarantined()).hasSize(3);
            assertThat(result.quarantined())
                    .extracting(QuarantinedRow::rowIdentifier)
                    .containsExactlyInAnyOrder("TXN-BAD-001", "TXN-BAD-002", "TXN-BAD-003");
        }
    }

    @Test
    void quarantinesNonNumericAmount() {
        ParseResult<LedgerTransaction> result = parser.parse(new StringReader(HEADER
                + "TXN-1,MERCH-1,ORD-1,VISA,1234,N/A,USD,SALE,2026-06-06T12:00:00Z\n"));

        assertThat(result.valid()).isEmpty();
        assertThat(result.quarantined()).hasSize(1);
        assertThat(result.quarantined().getFirst().reasons())
                .anyMatch(reason -> reason.contains("gross_amount"));
    }

    @Test
    void quarantinesMissingCardType() {
        ParseResult<LedgerTransaction> result = parser.parse(new StringReader(HEADER
                + "TXN-1,MERCH-1,ORD-1,,1234,88.00,USD,SALE,2026-06-06T12:00:00Z\n"));

        assertThat(result.quarantined()).hasSize(1);
        assertThat(result.quarantined().getFirst().reasons())
                .anyMatch(reason -> reason.contains("card_type"));
    }

    @Test
    void quarantinesUnsupportedCurrency() {
        ParseResult<LedgerTransaction> result = parser.parse(new StringReader(HEADER
                + "TXN-1,MERCH-1,ORD-1,VISA,1234,88.00,EUR,SALE,2026-06-06T12:00:00Z\n"));

        assertThat(result.quarantined()).hasSize(1);
        assertThat(result.quarantined().getFirst().reasons())
                .anyMatch(reason -> reason.contains("currency"));
    }

    @Test
    void collectsAllReasonsForARowWithMultipleDefects() {
        ParseResult<LedgerTransaction> result = parser.parse(new StringReader(HEADER
                + "TXN-1,MERCH-1,ORD-1,,1234,N/A,EUR,SALE,2026-06-06T12:00:00Z\n"));

        List<String> reasons = result.quarantined().getFirst().reasons();
        assertThat(reasons).hasSize(3);
    }

    @Test
    void parsesRefundWithNegativeAmount() {
        ParseResult<LedgerTransaction> result = parser.parse(new StringReader(HEADER
                + "TXN-1,MERCH-1,ORD-1,VISA,1234,-88.00,USD,REFUND,2026-06-06T12:00:00Z\n"));

        assertThat(result.valid()).hasSize(1);
        assertThat(result.valid().getFirst().type()).isEqualTo(TransactionType.REFUND);
    }

    @Test
    void quarantinesRefundWithPositiveAmount() {
        ParseResult<LedgerTransaction> result = parser.parse(new StringReader(HEADER
                + "TXN-1,MERCH-1,ORD-1,VISA,1234,88.00,USD,REFUND,2026-06-06T12:00:00Z\n"));

        assertThat(result.quarantined()).hasSize(1);
    }
}
