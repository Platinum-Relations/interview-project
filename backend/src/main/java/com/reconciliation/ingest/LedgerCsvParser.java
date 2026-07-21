package com.reconciliation.ingest;

import com.reconciliation.domain.CardType;
import com.reconciliation.domain.LedgerTransaction;
import com.reconciliation.domain.TransactionType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses internal_transactions.csv into typed {@link LedgerTransaction}s.
 * Rows that fail structural validation are quarantined with every reason collected,
 * so one pass tells ops everything wrong with a row.
 */
public class LedgerCsvParser {

    private static final String SUPPORTED_CURRENCY = "USD";

    public ParseResult<LedgerTransaction> parse(Reader reader) {
        List<LedgerTransaction> valid = new ArrayList<>();
        List<QuarantinedRow> quarantined = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .get();

        try (CSVParser parser = CSVParser.parse(reader, format)) {
            for (CSVRecord record : parser) {
                parseRow(record, valid, quarantined);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read internal transactions CSV", e);
        }
        return new ParseResult<>(valid, quarantined);
    }

    private void parseRow(CSVRecord record, List<LedgerTransaction> valid, List<QuarantinedRow> quarantined) {
        List<String> reasons = new ArrayList<>();

        String txnId = requireText(record, "internal_txn_id", reasons);
        String merchantId = requireText(record, "merchant_id", reasons);
        String merchantRef = requireText(record, "merchant_ref", reasons);
        String cardLast4 = requireText(record, "card_last4", reasons);

        CardType cardType = CardType.parse(get(record, "card_type"))
                .orElseGet(() -> {
                    reasons.add("Unknown or missing card_type: '" + get(record, "card_type") + "'");
                    return null;
                });

        TransactionType type = TransactionType.parse(get(record, "type"))
                .orElseGet(() -> {
                    reasons.add("Unknown or missing type: '" + get(record, "type") + "'");
                    return null;
                });

        BigDecimal grossAmount = parseAmount(get(record, "gross_amount"), "gross_amount", reasons);

        String currency = requireText(record, "currency", reasons);
        if (currency != null && !SUPPORTED_CURRENCY.equals(currency)) {
            reasons.add("Unsupported currency: '" + currency + "' (only USD is supported)");
        }

        Instant capturedAt = parseInstant(get(record, "captured_at"), reasons);

        if (grossAmount != null && type != null) {
            if (type == TransactionType.REFUND && grossAmount.signum() > 0) {
                reasons.add("REFUND with positive gross_amount: " + grossAmount);
            }
            if (type == TransactionType.SALE && grossAmount.signum() <= 0) {
                reasons.add("SALE with non-positive gross_amount: " + grossAmount);
            }
        }

        if (!reasons.isEmpty()) {
            String identifier = txnId != null ? txnId : "csv line " + record.getRecordNumber();
            quarantined.add(new QuarantinedRow(RowSource.INTERNAL, identifier, record.toString(), List.copyOf(reasons)));
            return;
        }

        valid.add(new LedgerTransaction(
                txnId, merchantId, merchantRef, cardType, cardLast4,
                grossAmount, currency, type, capturedAt));
    }

    private String get(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String value = record.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireText(CSVRecord record, String column, List<String> reasons) {
        String value = get(record, column);
        if (value == null) {
            reasons.add("Missing required field: " + column);
        }
        return value;
    }

    private BigDecimal parseAmount(String raw, String field, List<String> reasons) {
        if (raw == null) {
            reasons.add("Missing required field: " + field);
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            reasons.add("Non-numeric " + field + ": '" + raw + "'");
            return null;
        }
    }

    private Instant parseInstant(String raw, List<String> reasons) {
        if (raw == null) {
            reasons.add("Missing required field: captured_at");
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            reasons.add("Unparseable captured_at: '" + raw + "'");
            return null;
        }
    }
}
