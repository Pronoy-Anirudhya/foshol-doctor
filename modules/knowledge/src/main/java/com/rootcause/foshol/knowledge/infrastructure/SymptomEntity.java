package com.rootcause.foshol.knowledge.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "symptom")
public class SymptomEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 48)
    private String code;

    @Column(name = "name_bn", nullable = false, length = 120)
    private String nameBn;

    @Column(name = "name_en", length = 120)
    private String nameEn;

    @Column(nullable = false, length = 16)
    private String organ;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SymptomEntity() {}

    public UUID getId() {
        return id;
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

    public String getOrgan() {
        return organ;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
