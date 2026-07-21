package com.reconciliation.ingest;

import java.util.List;

/**
 * A row that failed structural validation and is excluded from reconciliation.
 * Keeps the raw content and every reason it failed so ops can inspect and fix upstream.
 */
public record QuarantinedRow(
        RowSource source,
        String rowIdentifier,
        String rawContent,
        List<String> reasons) {
}
