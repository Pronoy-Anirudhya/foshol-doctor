package com.rootcause.foshol.knowledge.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "model_label_map")
public class ModelLabelMapEntity {

    @Id
    private UUID id;

    @Column(name = "model_id", nullable = false, length = 160)
    private String modelId;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "raw_label", nullable = false, length = 160)
    private String rawLabel;

    @Column(name = "disease_id", nullable = false)
    private UUID diseaseId;

    protected ModelLabelMapEntity() {}

    public UUID getId() {
        return id;
    }

    public String getModelId() {
        return modelId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getRawLabel() {
        return rawLabel;
    }

    public UUID getDiseaseId() {
        return diseaseId;
    }
}
