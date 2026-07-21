package com.reconciliation.api.dto;

public record QuarantineDto(String source, String rowIdentifier, String rawContent, String reasons) {
}
