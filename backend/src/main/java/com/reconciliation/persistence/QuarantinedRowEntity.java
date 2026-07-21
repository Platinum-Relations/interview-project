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

/** A malformed input row excluded from reconciliation, kept for ops inspection. */
@Entity
@Table(name = "quarantined_row")
public class QuarantinedRowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id")
    private ImportRunEntity run;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(nullable = false)
    private String rowIdentifier;

    @Column(nullable = false, length = 2048)
    private String rawContent;

    @Column(nullable = false, length = 1024)
    private String reasons;

    protected QuarantinedRowEntity() {
    }

    public QuarantinedRowEntity(ImportRunEntity run, String source, String rowIdentifier, String rawContent, String reasons) {
        this.run = run;
        this.source = source;
        this.rowIdentifier = rowIdentifier;
        this.rawContent = rawContent;
        this.reasons = reasons;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getRowIdentifier() {
        return rowIdentifier;
    }

    public String getRawContent() {
        return rawContent;
    }

    public String getReasons() {
        return reasons;
    }
}
