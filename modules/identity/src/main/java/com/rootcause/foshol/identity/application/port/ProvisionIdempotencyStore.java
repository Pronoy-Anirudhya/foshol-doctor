package com.rootcause.foshol.identity.application.port;

import java.util.Optional;
import java.util.UUID;

public interface ProvisionIdempotencyStore {

    Optional<IdempotencySnapshot> findByKey(UUID key);

    void saveAndFlush(IdempotencySnapshot row);
}
