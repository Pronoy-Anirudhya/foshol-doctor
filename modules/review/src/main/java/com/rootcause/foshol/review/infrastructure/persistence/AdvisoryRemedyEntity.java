package com.rootcause.foshol.review.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "advisory_remedy")
@IdClass(AdvisoryRemedyEntity.Key.class)
public class AdvisoryRemedyEntity {

    @Id
    @Column(name = "advisory_id", nullable = false)
    private UUID advisoryId;

    @Id
    @Column(name = "remedy_id", nullable = false)
    private UUID remedyId;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected AdvisoryRemedyEntity() {}

    public AdvisoryRemedyEntity(UUID advisoryId, UUID remedyId, short displayOrder) {
        this.advisoryId = advisoryId;
        this.remedyId = remedyId;
        this.displayOrder = displayOrder;
    }

    public UUID getAdvisoryId() {
        return advisoryId;
    }

    public UUID getRemedyId() {
        return remedyId;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public static final class Key implements Serializable {
        private UUID advisoryId;
        private UUID remedyId;

        public Key() {}

        public Key(UUID advisoryId, UUID remedyId) {
            this.advisoryId = advisoryId;
            this.remedyId = remedyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(advisoryId, key.advisoryId) && Objects.equals(remedyId, key.remedyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(advisoryId, remedyId);
        }
    }
}
