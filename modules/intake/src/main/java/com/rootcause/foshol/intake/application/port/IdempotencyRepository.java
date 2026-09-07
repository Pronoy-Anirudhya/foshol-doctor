package com.rootcause.foshol.intake.application.port;

import com.rootcause.foshol.intake.domain.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository {

    void deleteExpired(Instant now);

    Optional<IdempotencyRecord> findByKey(UUID key);

    void insert(IdempotencyRecord record);
}
