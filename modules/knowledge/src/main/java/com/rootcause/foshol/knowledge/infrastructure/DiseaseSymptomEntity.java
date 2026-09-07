package com.rootcause.foshol.knowledge.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "disease_symptom")
@IdClass(DiseaseSymptomEntity.DiseaseSymptomId.class)
public class DiseaseSymptomEntity {

    @Id
    @Column(name = "disease_id", nullable = false)
    private UUID diseaseId;

    @Id
    @Column(name = "symptom_id", nullable = false)
    private UUID symptomId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal weight;

    protected DiseaseSymptomEntity() {}

    public UUID getDiseaseId() {
        return diseaseId;
    }

    public UUID getSymptomId() {
        return symptomId;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public static class DiseaseSymptomId implements Serializable {

        private UUID diseaseId;
        private UUID symptomId;

        public DiseaseSymptomId() {}

        public DiseaseSymptomId(UUID diseaseId, UUID symptomId) {
            this.diseaseId = diseaseId;
            this.symptomId = symptomId;
        }

        public UUID getDiseaseId() {
            return diseaseId;
        }

        public void setDiseaseId(UUID diseaseId) {
            this.diseaseId = diseaseId;
        }

        public UUID getSymptomId() {
            return symptomId;
        }

        public void setSymptomId(UUID symptomId) {
            this.symptomId = symptomId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DiseaseSymptomId that)) {
                return false;
            }
            return Objects.equals(diseaseId, that.diseaseId) && Objects.equals(symptomId, that.symptomId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(diseaseId, symptomId);
        }
    }
}
