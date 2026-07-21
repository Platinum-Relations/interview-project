package com.reconciliation.ingest;

import com.reconciliation.domain.Settlement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementJsonParserTest {

    private final SettlementJsonParser parser = new SettlementJsonParser();

    @Test
    void parsesTestFixtureQuarantiningTwoMalformedRows() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("../test/processor_settlement.json"))) {
            ParseResult<Settlement> result = parser.parse(reader);

            assertThat(result.valid()).hasSize(17);
            assertThat(result.quarantined()).hasSize(2);
        }
    }

    @Test
    void blankMerchantRefIsValidAndNormalizedToNull() {
        ParseResult<Settlement> result = parser.parse(new StringReader("""
                [{
                  "network_ref": "ARN1", "merchant_ref": "", "merchant_id": "MERCH-1",
                  "card_last4": "1234", "card_type": "VISA", "settled_amount": "97.75",
                  "interchange_fee": "1.90", "processor_fee": "0.35",
                  "currency": "USD", "settlement_date": "2026-06-05"
                }]
                """));

        assertThat(result.valid()).hasSize(1);
        assertThat(result.valid().getFirst().merchantRef()).isNull();
        assertThat(result.valid().getFirst().hasMerchantRef()).isFalse();
    }

    @Test
    void quarantinesMissingSettledAmount() {
        ParseResult<Settlement> result = parser.parse(new StringReader("""
                [{
                  "network_ref": "ARN1", "merchant_ref": "ORD-1", "merchant_id": "MERCH-1",
                  "card_last4": "1234", "card_type": "VISA",
                  "interchange_fee": "1.90", "processor_fee": "0.35",
                  "currency": "USD", "settlement_date": "2026-06-05"
                }]
                """));

        assertThat(result.valid()).isEmpty();
        assertThat(result.quarantined()).hasSize(1);
        assertThat(result.quarantined().getFirst().reasons())
                .anyMatch(reason -> reason.contains("settled_amount"));
    }

    @Test
    void quarantinesUnsupportedCurrency() {
        ParseResult<Settlement> result = parser.parse(new StringReader("""
                [{
                  "network_ref": "ARN1", "merchant_ref": "ORD-1", "merchant_id": "MERCH-1",
                  "card_last4": "1234", "card_type": "AMEX", "settled_amount": "97.75",
                  "interchange_fee": "1.90", "processor_fee": "0.35",
                  "currency": "EUR", "settlement_date": "2026-06-05"
                }]
                """));

        assertThat(result.quarantined()).hasSize(1);
    }

    @Test
    void rejectsNonArrayPayload() {
        assertThatThrownBy(() -> parser.parse(new StringReader("{\"not\": \"an array\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
