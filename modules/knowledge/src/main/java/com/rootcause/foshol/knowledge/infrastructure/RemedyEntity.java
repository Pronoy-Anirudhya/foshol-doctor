package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.common.RemedyRateBasis;
import com.rootcause.foshol.common.RemedyRateUnit;
import com.rootcause.foshol.common.RemedyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "remedy")
public class RemedyEntity {

    @Id
    private UUID id;

    @Column(name = "disease_id", nullable = false)
    private UUID diseaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RemedyType type;

    @Column(name = "title_bn", nullable = false, length = 200)
    private String titleBn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps_bn", nullable = false)
    private String stepsBn;

    @Column(name = "dosage_bn")
    private String dosageBn;

    @Column(name = "phi_days")
    private Short phiDays;

    @Column(name = "cost_tier", nullable = false, length = 8)
    private String costTier;

    @Column(nullable = false, length = 8)
    private String efficacy;

    @Column(name = "source_ref", nullable = false)
    private String sourceRef;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "rate_amount", precision = 12, scale = 4)
    private BigDecimal rateAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_unit", length = 8)
    private RemedyRateUnit rateUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_basis", length = 16)
    private RemedyRateBasis rateBasis;

    @Column(name = "rate_notes_bn")
    private String rateNotesBn;

    protected RemedyEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getDiseaseId() {
        return diseaseId;
    }

    public RemedyType getType() {
        return type;
    }

    public String getTitleBn() {
        return titleBn;
    }

    public String getStepsBn() {
        return stepsBn;
    }

    public String getDosageBn() {
        return dosageBn;
    }

    public Short getPhiDays() {
        return phiDays;
    }

    public String getCostTier() {
        return costTier;
    }

    public String getEfficacy() {
        return efficacy;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public BigDecimal getRateAmount() {
        return rateAmount;
    }

    public RemedyRateUnit getRateUnit() {
        return rateUnit;
    }

    public RemedyRateBasis getRateBasis() {
        return rateBasis;
    }

    public String getRateNotesBn() {
        return rateNotesBn;
    }
}
