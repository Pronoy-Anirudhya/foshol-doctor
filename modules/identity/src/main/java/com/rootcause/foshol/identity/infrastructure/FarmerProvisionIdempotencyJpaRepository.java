package com.rootcause.foshol.identity.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmerProvisionIdempotencyJpaRepository
        extends JpaRepository<FarmerProvisionIdempotencyEntity, UUID> {}
