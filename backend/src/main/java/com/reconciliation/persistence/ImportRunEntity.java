package com.reconciliation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One import + reconciliation run. The content hash of both input files makes
 * re-imports idempotent: importing identical files returns the existing run
 * instead of creating a duplicate.
 */
@Entity
@Table(name = "import_run")
public class ImportRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant importedAt;

    @Column(nullable = false)
    private String internalFileName;

    @Column(nullable = false)
    private String settlementFileName;

    @Column(nullable = false, unique = true, length = 64)
    private String contentHash;

    // Run-level money facts, computed once at import time.
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedPayout;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal actualSettled;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalFeesReported;

    private int validInternalCount;
    private int validSettlementCount;
    private int quarantinedCount;

    protected ImportRunEntity() {
    }

    public ImportRunEntity(
            Instant importedAt,
            String internalFileName,
            String settlementFileName,
            String contentHash,
            BigDecimal expectedPayout,
            BigDecimal actualSettled,
            BigDecimal totalFeesReported,
            int validInternalCount,
            int validSettlementCount,
            int quarantinedCount) {
        this.importedAt = importedAt;
        this.internalFileName = internalFileName;
        this.settlementFileName = settlementFileName;
        this.contentHash = contentHash;
        this.expectedPayout = expectedPayout;
        this.actualSettled = actualSettled;
        this.totalFeesReported = totalFeesReported;
        this.validInternalCount = validInternalCount;
        this.validSettlementCount = validSettlementCount;
        this.quarantinedCount = quarantinedCount;
    }

    public Long getId() {
        return id;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public String getInternalFileName() {
        return internalFileName;
    }

    public String getSettlementFileName() {
        return settlementFileName;
    }

    public String getContentHash() {
        return contentHash;
    }

    public BigDecimal getExpectedPayout() {
        return expectedPayout;
    }

    public BigDecimal getActualSettled() {
        return actualSettled;
    }

    public BigDecimal getTotalFeesReported() {
        return totalFeesReported;
    }

    public int getValidInternalCount() {
        return validInternalCount;
    }

    public int getValidSettlementCount() {
        return validSettlementCount;
    }

    public int getQuarantinedCount() {
        return quarantinedCount;
    }
}
