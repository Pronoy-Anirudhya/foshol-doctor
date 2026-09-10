package com.rootcause.foshol.identity.infrastructure.adapter;

import com.rootcause.foshol.identity.application.port.IdempotencySnapshot;
import com.rootcause.foshol.identity.application.port.ProvisionIdempotencyStore;
import com.rootcause.foshol.identity.infrastructure.persistence.FarmerProvisionIdempotencyEntity;
import com.rootcause.foshol.identity.infrastructure.persistence.FarmerProvisionIdempotencyJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProvisionIdempotencyStore implements ProvisionIdempotencyStore {

    private final FarmerProvisionIdempotencyJpaRepository idempotency;

    public JpaProvisionIdempotencyStore(FarmerProvisionIdempotencyJpaRepository idempotency) {
        this.idempotency = idempotency;
    }

    @Override
    public Optional<IdempotencySnapshot> findByKey(UUID key) {
        return idempotency.findById(key).map(JpaProvisionIdempotencyStore::toSnapshot);
    }

    @Override
    public void saveAndFlush(IdempotencySnapshot row) {
        idempotency.saveAndFlush(toEntity(row));
    }

    private static IdempotencySnapshot toSnapshot(FarmerProvisionIdempotencyEntity entity) {
        return new IdempotencySnapshot(
                entity.getKey(),
                entity.getOfficerId(),
                entity.getRequestHash(),
                entity.getFarmerId(),
                null,
                null);
    }

    private static FarmerProvisionIdempotencyEntity toEntity(IdempotencySnapshot row) {
        return new FarmerProvisionIdempotencyEntity(
                row.key(), row.officerId(), row.requestHash(), row.farmerId(), row.createdAt(), row.expiresAt());
    }
}
