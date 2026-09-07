package com.rootcause.foshol.identity.api;

import java.util.Optional;
import java.util.UUID;

public interface FarmerLookupApi {

    Optional<FarmerView> findById(UUID farmerId);

    Optional<FarmerView> findByPhone(String e164Phone);
}
