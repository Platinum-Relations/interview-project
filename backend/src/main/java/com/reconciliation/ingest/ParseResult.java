package com.reconciliation.ingest;

import java.util.List;

/** Outcome of parsing a source file: the typed valid rows plus everything quarantined. */
public record ParseResult<T>(List<T> valid, List<QuarantinedRow> quarantined) {
}
