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
@Table(name = "symptom_phrase")
public class SymptomPhraseEntity {

    @Id
    private UUID id;

    @Column(name = "symptom_id", nullable = false)
    private UUID symptomId;

    @Column(name = "phrase_bn", nullable = false)
    private String phraseBn;

    @Column(name = "normalised_bn", nullable = false)
    private String normalisedBn;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected SymptomPhraseEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getSymptomId() {
        return symptomId;
    }

    public String getPhraseBn() {
        return phraseBn;
    }

    public String getNormalisedBn() {
        return normalisedBn;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
