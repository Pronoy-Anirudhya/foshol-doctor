package com.rootcause.foshol.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "farmer")
public class FarmerEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Column(name = "phone_enc", nullable = false)
    private byte[] phoneEnc;

    @Column(name = "district_code", nullable = false, length = 8)
    private String districtCode;

    @Column(name = "preferred_language", nullable = false, length = 2)
    private String preferredLanguage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FarmerEntity() {}

    public FarmerEntity(
            UUID id,
            String name,
            String phoneHash,
            byte[] phoneEnc,
            String districtCode,
            String preferredLanguage,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.phoneHash = phoneHash;
        this.phoneEnc = phoneEnc;
        this.districtCode = districtCode;
        this.preferredLanguage = preferredLanguage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }
}
