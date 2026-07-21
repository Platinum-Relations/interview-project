package com.reconciliation.ingest;

import com.reconciliation.domain.CardType;
import com.reconciliation.domain.Settlement;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses processor_settlement.json into typed {@link Settlement}s.
 * A blank merchant_ref is legitimate (the processor doesn't always echo it) and is
 * normalized to null; every other structural defect quarantines the row.
 */
public class SettlementJsonParser {

    private static final String SUPPORTED_CURRENCY = "USD";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParseResult<Settlement> parse(Reader reader) {
        JsonNode root;
        try {
            root = objectMapper.readTree(reader);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Settlement file is not valid JSON", e);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("Settlement file must be a JSON array of settlement records");
        }

        List<Settlement> valid = new ArrayList<>();
        List<QuarantinedRow> quarantined = new ArrayList<>();
        int index = 0;
        for (JsonNode node : root) {
            parseRow(node, index++, valid, quarantined);
        }
        return new ParseResult<>(valid, quarantined);
    }

    private void parseRow(JsonNode node, int index, List<Settlement> valid, List<QuarantinedRow> quarantined) {
        List<String> reasons = new ArrayList<>();

        String networkRef = requireText(node, "network_ref", reasons);
        String merchantId = requireText(node, "merchant_id", reasons);
        String cardLast4 = requireText(node, "card_last4", reasons);

        // Blank/absent merchant_ref is a known condition, not a defect.
        String merchantRef = optionalText(node, "merchant_ref");

        CardType cardType = CardType.parse(optionalText(node, "card_type"))
                .orElseGet(() -> {
                    reasons.add("Unknown or missing card_type: '" + optionalText(node, "card_type") + "'");
                    return null;
                });

        BigDecimal settledAmount = parseAmount(node, "settled_amount", reasons);
        BigDecimal interchangeFee = parseAmount(node, "interchange_fee", reasons);
        BigDecimal processorFee = parseAmount(node, "processor_fee", reasons);

        String currency = requireText(node, "currency", reasons);
        if (currency != null && !SUPPORTED_CURRENCY.equals(currency)) {
            reasons.add("Unsupported currency: '" + currency + "' (only USD is supported)");
        }

        LocalDate settlementDate = parseDate(node, reasons);

        if (!reasons.isEmpty()) {
            String identifier = networkRef != null ? networkRef : "settlement index " + index;
            quarantined.add(new QuarantinedRow(RowSource.SETTLEMENT, identifier, node.toString(), List.copyOf(reasons)));
            return;
        }

        valid.add(new Settlement(
                networkRef, merchantRef, merchantId, cardLast4, cardType,
                settledAmount, interchangeFee, processorFee, currency, settlementDate));
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private String requireText(JsonNode node, String field, List<String> reasons) {
        String value = optionalText(node, field);
        if (value == null) {
            reasons.add("Missing required field: " + field);
        }
        return value;
    }

    private BigDecimal parseAmount(JsonNode node, String field, List<String> reasons) {
        String raw = optionalText(node, field);
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

    private LocalDate parseDate(JsonNode node, List<String> reasons) {
        String raw = optionalText(node, "settlement_date");
        if (raw == null) {
            reasons.add("Missing required field: settlement_date");
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            reasons.add("Unparseable settlement_date: '" + raw + "'");
            return null;
        }
    }
}
