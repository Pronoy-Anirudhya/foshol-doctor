package com.rootcause.foshol.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "farmer_provision_idempotency")
public class FarmerProvisionIdempotencyEntity {

    @Id
    private UUID key;

    @Column(name = "officer_id", nullable = false)
    private UUID officerId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "farmer_id", nullable = false)
    private UUID farmerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected FarmerProvisionIdempotencyEntity() {}

    public FarmerProvisionIdempotencyEntity(
            UUID key, UUID officerId, String requestHash, UUID farmerId, Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.officerId = officerId;
        this.requestHash = requestHash;
        this.farmerId = farmerId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getKey() {
        return key;
    }

    public UUID getOfficerId() {
        return officerId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getFarmerId() {
        return farmerId;
    }
}
