package com.reconciliation.persistence;

import com.reconciliation.engine.Classification;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A persisted reconciliation outcome. The internal-side columns are null for
 * settlement-only items (UNMATCHED_SETTLEMENT); the settlement rows live in a
 * child table since duplicates and splits attribute several rows to one item.
 */
@Entity
@Table(name = "reconciliation_item", indexes = {
        @Index(name = "idx_item_run_classification", columnList = "run_id, classification"),
        @Index(name = "idx_item_run_merchant", columnList = "run_id, merchantId")
})
public class ReconciliationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id")
    private ImportRunEntity run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Classification classification;

    @Column(nullable = false, length = 1024)
    private String reason;

    /** Merchant for rollups: from the internal side when present, else from the settlement. */
    @Column(nullable = false)
    private String merchantId;

    // Internal ledger side (null for settlement-only items).
    private String internalTxnId;
    private String merchantRef;
    private String cardType;
    private String cardLast4;

    @Column(precision = 14, scale = 2)
    private BigDecimal grossAmount;

    private String transactionType;
    private Instant capturedAt;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SettlementRowEntity> settlementRows = new ArrayList<>();

    protected ReconciliationItemEntity() {
    }

    public ReconciliationItemEntity(ImportRunEntity run, Classification classification, String reason, String merchantId) {
        this.run = run;
        this.classification = classification;
        this.reason = reason;
        this.merchantId = merchantId;
    }

    public void setInternalSide(String internalTxnId, String merchantRef, String cardType, String cardLast4,
                                BigDecimal grossAmount, String transactionType, Instant capturedAt) {
        this.internalTxnId = internalTxnId;
        this.merchantRef = merchantRef;
        this.cardType = cardType;
        this.cardLast4 = cardLast4;
        this.grossAmount = grossAmount;
        this.transactionType = transactionType;
        this.capturedAt = capturedAt;
    }

    public void addSettlementRow(SettlementRowEntity row) {
        settlementRows.add(row);
    }

    public Long getId() {
        return id;
    }

    public ImportRunEntity getRun() {
        return run;
    }

    public Classification getClassification() {
        return classification;
    }

    public String getReason() {
        return reason;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getInternalTxnId() {
        return internalTxnId;
    }

    public String getMerchantRef() {
        return merchantRef;
    }

    public String getCardType() {
        return cardType;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public List<SettlementRowEntity> getSettlementRows() {
        return settlementRows;
    }
}
