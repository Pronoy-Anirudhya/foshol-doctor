package com.rootcause.foshol.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "farmer")
public class FarmerEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Column(name = "phone_enc", nullable = false)
    private byte[] phoneEnc;

    @Column(name = "district_code", nullable = false, length = 16)
    private String districtCode;

    @Column(name = "division_code", nullable = false, length = 8)
    private String divisionCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "preferred_language", nullable = false, length = 2)
    private String preferredLanguage;

    @Column(name = "registered_by")
    private UUID registeredBy;

    @Column(name = "registration_source", nullable = false, length = 16)
    private String registrationSource;

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
            String divisionCode,
            String preferredLanguage,
            Instant createdAt,
            Instant updatedAt) {
        this(id, name, phoneHash, phoneEnc, districtCode, divisionCode, preferredLanguage, null, "MIGRATION", createdAt, updatedAt);
    }

    public FarmerEntity(
            UUID id,
            String name,
            String phoneHash,
            byte[] phoneEnc,
            String districtCode,
            String divisionCode,
            String preferredLanguage,
            UUID registeredBy,
            String registrationSource,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.phoneHash = phoneHash;
        this.phoneEnc = phoneEnc;
        this.districtCode = districtCode;
        this.divisionCode = divisionCode;
        this.preferredLanguage = preferredLanguage;
        this.registeredBy = registeredBy;
        this.registrationSource = registrationSource;
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

    public String getDivisionCode() {
        return divisionCode;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public UUID getRegisteredBy() {
        return registeredBy;
    }

    public String getRegistrationSource() {
        return registrationSource;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
