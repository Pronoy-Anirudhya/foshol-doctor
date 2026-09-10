package com.rootcause.foshol.knowledge.infrastructure.persistence;

import com.rootcause.foshol.common.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "disease")
public class DiseaseEntity {

    @Id
    private UUID id;

    @Column(name = "crop_id", nullable = false)
    private UUID cropId;

    @Column(nullable = false, length = 48)
    private String code;

    @Column(name = "name_bn", nullable = false, length = 120)
    private String nameBn;

    @Column(name = "name_en", length = 120)
    private String nameEn;

    @Column(name = "description_bn")
    private String descriptionBn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Severity severity;

    @Column(name = "is_healthy", nullable = false)
    private boolean healthy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected DiseaseEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getCropId() {
        return cropId;
    }

    public String getCode() {
        return code;
    }

    public String getNameBn() {
        return nameBn;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescriptionBn() {
        return descriptionBn;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
