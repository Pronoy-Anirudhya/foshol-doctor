package com.rootcause.foshol.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public interface FarmerDirectoryRow {

    UUID getId();

    String getName();

    String getDistrictCode();

    String getDivisionCode();

    String getPreferredLanguage();

    Instant getCreatedAt();

    UUID getRegisteredBy();

    String getRegistrationSource();
}
