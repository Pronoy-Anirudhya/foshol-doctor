package com.rootcause.foshol.identity.application.port;

import java.util.Optional;
import java.util.UUID;

public interface FarmerStore {

    Optional<FarmerSnapshot> findByPhoneHash(String phoneHash);

    Optional<FarmerSnapshot> findById(UUID id);

    void saveAndFlush(FarmerSnapshot farmer);
}
