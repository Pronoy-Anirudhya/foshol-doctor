package com.rootcause.foshol.identity.application.port;

import java.util.Optional;
import java.util.UUID;

public interface OfficerStore {

    Optional<OfficerSnapshot> findById(UUID id);

    Optional<OfficerSnapshot> findByUsername(String username);
}
