package com.reconciliation.fees;

import com.reconciliation.domain.CardType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * The published fee schedule (fee_schedule.json): per-card interchange rates
 * plus the flat processor markup applied to every card.
 */
public record FeeSchedule(Map<CardType, Rate> interchange, Rate processorMarkup) {

    public record Rate(BigDecimal percent, BigDecimal flat) {
    }

    public static FeeSchedule load(Reader reader) {
        JsonNode root = new ObjectMapper().readTree(reader);

        Map<CardType, Rate> interchange = new EnumMap<>(CardType.class);
        JsonNode interchangeNode = root.required("interchange");
        for (CardType cardType : CardType.values()) {
            interchange.put(cardType, rateFrom(interchangeNode.required(cardType.name())));
        }
        return new FeeSchedule(Map.copyOf(interchange), rateFrom(root.required("processor_markup")));
    }

    private static Rate rateFrom(JsonNode node) {
        return new Rate(
                new BigDecimal(node.required("percent").asString()),
                new BigDecimal(node.required("flat").asString()));
    }

    public Rate interchangeFor(CardType cardType) {
        return interchange.get(cardType);
    }
}
