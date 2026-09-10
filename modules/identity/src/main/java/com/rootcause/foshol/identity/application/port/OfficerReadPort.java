package com.rootcause.foshol.identity.application.port;

import java.util.Optional;
import java.util.UUID;

public interface OfficerReadPort {

    Optional<OfficerReadSnapshot> findReadById(UUID id);
}
