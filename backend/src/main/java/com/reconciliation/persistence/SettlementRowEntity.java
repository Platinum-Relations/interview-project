package com.reconciliation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One settlement-file row attributed to a reconciliation item.
 * Duplicates and splits give one item several of these.
 */
@Entity
@Table(name = "settlement_row")
public class SettlementRowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private ReconciliationItemEntity item;

    @Column(nullable = false)
    private String networkRef;

    private String merchantRef;

    @Column(nullable = false)
    private String merchantId;

    private String cardType;
    private String cardLast4;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal settledAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal interchangeFee;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal processorFee;

    @Column(nullable = false)
    private LocalDate settlementDate;

    protected SettlementRowEntity() {
    }

    public SettlementRowEntity(
            ReconciliationItemEntity item,
            String networkRef,
            String merchantRef,
            String merchantId,
            String cardType,
            String cardLast4,
            BigDecimal settledAmount,
            BigDecimal interchangeFee,
            BigDecimal processorFee,
            LocalDate settlementDate) {
        this.item = item;
        this.networkRef = networkRef;
        this.merchantRef = merchantRef;
        this.merchantId = merchantId;
        this.cardType = cardType;
        this.cardLast4 = cardLast4;
        this.settledAmount = settledAmount;
        this.interchangeFee = interchangeFee;
        this.processorFee = processorFee;
        this.settlementDate = settlementDate;
    }

    public Long getId() {
        return id;
    }

    public String getNetworkRef() {
        return networkRef;
    }

    public String getMerchantRef() {
        return merchantRef;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getCardType() {
        return cardType;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public BigDecimal getInterchangeFee() {
        return interchangeFee;
    }

    public BigDecimal getProcessorFee() {
        return processorFee;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }
}
