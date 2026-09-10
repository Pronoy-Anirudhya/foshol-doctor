package com.rootcause.foshol.knowledge.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "crop")
public class CropEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 24)
    private String code;

    @Column(name = "name_bn", nullable = false, length = 120)
    private String nameBn;

    @Column(name = "name_en", length = 120)
    private String nameEn;

    @Column(name = "icon_key", nullable = false, length = 64)
    private String iconKey;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CropEntity() {}

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

    public String getIconKey() {
        return iconKey;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
