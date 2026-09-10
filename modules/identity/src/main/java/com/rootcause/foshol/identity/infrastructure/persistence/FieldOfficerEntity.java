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
@Table(name = "field_officer")
public class FieldOfficerEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Column(name = "phone_enc", nullable = false)
    private byte[] phoneEnc;

    @Column(name = "district_code", nullable = false, length = 16)
    private String districtCode;

    @Column(name = "division_code", nullable = false, length = 8)
    private String divisionCode;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FieldOfficerEntity() {}

    public FieldOfficerEntity(
            UUID id,
            String name,
            String username,
            String passwordHash,
            String phoneHash,
            byte[] phoneEnc,
            String districtCode,
            String divisionCode,
            String role,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.passwordHash = passwordHash;
        this.phoneHash = phoneHash;
        this.phoneEnc = phoneEnc;
        this.districtCode = districtCode;
        this.divisionCode = divisionCode;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public String getDivisionCode() {
        return divisionCode;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
